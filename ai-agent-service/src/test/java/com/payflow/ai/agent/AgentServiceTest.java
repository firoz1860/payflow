package com.payflow.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.ai.config.AiProperties;
import com.payflow.ai.conversation.AiConversation;
import com.payflow.ai.conversation.AiMessage;
import com.payflow.ai.conversation.ConversationService;
import com.payflow.ai.conversation.MessageRole;
import com.payflow.ai.investigation.Confidence;
import com.payflow.ai.investigation.EvidenceRef;
import com.payflow.ai.investigation.Fact;
import com.payflow.ai.investigation.InvestigationResult;
import com.payflow.ai.investigation.PaymentInvestigationService;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.CredentialSource;
import com.payflow.ai.llm.FakeLlmClient;
import com.payflow.ai.llm.LlmClient;
import com.payflow.ai.llm.LlmClientFactory;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.LlmCredentialProvider;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import com.payflow.ai.llm.ResolvedCredential;
import com.payflow.ai.metrics.AiMetrics;
import com.payflow.ai.prompt.PromptBuilder;
import com.payflow.ai.redaction.Redactor;
import com.payflow.ai.tools.ToolRegistry;
import com.payflow.ai.tools.ToolResult;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orchestration-level tests with the existing {@link FakeLlmClient} and Mockito — no real keys, no network. */
class AgentServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID assistantMessageId = UUID.randomUUID();

    private AiProperties properties;
    private LlmCredentialProvider credentialProvider;
    private LlmClientFactory llmClientFactory;
    private PaymentInvestigationService investigationService;
    private ConversationService conversationService;
    private LlmClient spyClient;
    private AgentService agentService;

    private PayFlowPrincipal principal() {
        return PayFlowPrincipal.forUser(userId, merchantId, Set.of("ai:use"));
    }

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setDefaultProvider("anthropic");

        credentialProvider = mock(LlmCredentialProvider.class);
        llmClientFactory = mock(LlmClientFactory.class);
        investigationService = mock(PaymentInvestigationService.class);
        conversationService = mock(ConversationService.class);
        ToolRegistry toolRegistry = mock(ToolRegistry.class);

        Redactor redactor = new Redactor();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PromptBuilder promptBuilder = new PromptBuilder(redactor);
        AiMetrics metrics = new AiMetrics(new SimpleMeterRegistry());

        spyClient = spy(new FakeLlmClient(AiProvider.ANTHROPIC,
                new LlmResponse("Payment pay_x is CAPTURED.", 11, 23, "fake-model")));

        agentService = new AgentService(properties, credentialProvider, llmClientFactory, investigationService,
                toolRegistry, promptBuilder, redactor, objectMapper, conversationService, metrics);

        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversationService.requireOwned(any(), eq(conversationId))).thenReturn(conversation);
        when(conversationService.history(eq(conversationId), anyInt())).thenReturn(List.of());
        AiMessage assistant = mock(AiMessage.class);
        when(assistant.getId()).thenReturn(assistantMessageId);
        when(conversationService.saveMessage(eq(conversationId), eq(MessageRole.ASSISTANT), any(), any()))
                .thenReturn(assistant);
    }

    private void stubCredential() {
        ResolvedCredential resolved = new ResolvedCredential(
                new LlmCredential(AiProvider.ANTHROPIC, "sk-ant-usersecret", null), CredentialSource.USER);
        when(credentialProvider.resolve(eq(userId), any())).thenReturn(Optional.of(resolved));
        when(llmClientFactory.forProvider(any())).thenReturn(spyClient);
    }

    @Test
    void noCredentialYieldsAiKeyRequired() {
        when(credentialProvider.resolve(eq(userId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentService.answer(principal(), conversationId, "Why did pay_x fail?", null, null))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.AI_KEY_REQUIRED);
    }

    @Test
    void featureDisabledYieldsAiUnavailable() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> agentService.answer(principal(), conversationId, "hello", null, null))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void happyPathReturnsAnswerWithEvidenceConfidenceAndToolCallsAndRedactsEvidence() {
        stubCredential();
        Fact statusFact = new Fact("PAYMENT_STATUS", "CAPTURED", "payment-service");
        Fact leakyFact = new Fact("PAYMENT_NOTE", "operator pasted key sk-ant-SECRETVALUE1234 into the note",
                "payment-service");
        EvidenceRef ref = new EvidenceRef("payment-service", "payment", "pay_x", true);
        ToolResult toolResult = ToolResult.success("get_payment", "payment:pay_x", 5, Map.of("status", "CAPTURED"));
        InvestigationResult investigation = new InvestigationResult("pay_x", true,
                List.of(statusFact, leakyFact), List.of("Provider confirmed -> likely normal."),
                List.of(), List.of("A warning"), Confidence.HIGH, List.of(ref), List.of(toolResult));
        when(investigationService.investigate(any(), eq("pay_x"), any())).thenReturn(investigation);

        AnswerResponse response = agentService.answer(principal(), conversationId,
                "What happened to this payment?", "PAYMENT", "pay_x");

        assertThat(response.messageId()).isEqualTo(assistantMessageId);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.answer()).isEqualTo("Payment pay_x is CAPTURED.");
        assertThat(response.confidence()).isEqualTo("HIGH");
        assertThat(response.evidence()).hasSize(1);
        assertThat(response.evidence().get(0).source()).isEqualTo("payment-service");
        assertThat(response.toolCalls()).extracting(ToolCall::name).contains("get_payment");
        assertThat(response.warnings()).contains("A warning");

        // Assert the Redactor was applied to the evidence BEFORE the prompt reached the client.
        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(spyClient).complete(any(), captor.capture());
        LlmRequest sent = captor.getValue();
        String promptText = sent.systemPrompt() + "\n"
                + sent.messages().stream().map(m -> m.content()).reduce("", (a, b) -> a + "\n" + b);
        assertThat(promptText).contains(Redactor.REDACTED);
        assertThat(promptText).doesNotContain("SECRETVALUE1234");
        // The user message carries the fenced untrusted-data block.
        assertThat(promptText).contains(PromptBuilder.FENCE_OPEN);
    }

    @Test
    void persistsUserAndAssistantMessages() {
        stubCredential();
        when(investigationService.investigate(any(), eq("pay_x"), any()))
                .thenReturn(new InvestigationResult("pay_x", true, List.of(), List.of(), List.of(), List.of(),
                        Confidence.LOW, List.of(), List.of()));

        agentService.answer(principal(), conversationId, "pay_x status?", "PAYMENT", "pay_x");

        verify(conversationService).saveMessage(eq(conversationId), eq(MessageRole.USER), any(), any());
        verify(conversationService).saveMessage(eq(conversationId), eq(MessageRole.ASSISTANT), any(), any());
        verify(conversationService).saveToolExecutions(eq(conversationId), eq(assistantMessageId), any());
    }
}

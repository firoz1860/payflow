package com.payflow.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.config.AiProperties;
import com.payflow.ai.conversation.AiConversation;
import com.payflow.ai.conversation.AiMessage;
import com.payflow.ai.conversation.ConversationService;
import com.payflow.ai.conversation.MessageRole;
import com.payflow.ai.investigation.EvidenceRef;
import com.payflow.ai.investigation.InvestigationResult;
import com.payflow.ai.investigation.PaymentInvestigationService;
import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmClientFactory;
import com.payflow.ai.llm.LlmCredentialProvider;
import com.payflow.ai.llm.LlmMessage;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import com.payflow.ai.llm.ResolvedCredential;
import com.payflow.ai.metrics.AiMetrics;
import com.payflow.ai.prompt.PromptBuilder;
import com.payflow.ai.prompt.SystemPrompt;
import com.payflow.ai.redaction.Redactor;
import com.payflow.ai.tools.ExplainIdempotencyContractTool;
import com.payflow.ai.tools.GetServiceCapabilityTool;
import com.payflow.ai.tools.ToolContext;
import com.payflow.ai.tools.ToolRegistry;
import com.payflow.ai.tools.ToolResult;
import com.payflow.ai.tools.ToolStatus;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The orchestration core. {@code answer(...)} is fully server-driven: it decides
 * which read-only tools to run, enforces tenancy inside them, redacts all evidence,
 * builds the fenced prompt, calls the BYOK LLM, and persists the turn. The LLM is
 * never allowed to choose a tool or a URL — it only narrates the deterministic
 * evidence the server gathered.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final Pattern PAYMENT_REF = Pattern.compile("\\b(pay_[A-Za-z0-9]+)\\b");

    private final AiProperties properties;
    private final LlmCredentialProvider credentialProvider;
    private final LlmClientFactory llmClientFactory;
    private final PaymentInvestigationService investigationService;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final Redactor redactor;
    private final ObjectMapper objectMapper;
    private final ConversationService conversationService;
    private final AiMetrics metrics;

    public AgentService(AiProperties properties,
                        LlmCredentialProvider credentialProvider,
                        LlmClientFactory llmClientFactory,
                        PaymentInvestigationService investigationService,
                        ToolRegistry toolRegistry,
                        PromptBuilder promptBuilder,
                        Redactor redactor,
                        ObjectMapper objectMapper,
                        ConversationService conversationService,
                        AiMetrics metrics) {
        this.properties = properties;
        this.credentialProvider = credentialProvider;
        this.llmClientFactory = llmClientFactory;
        this.investigationService = investigationService;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.redactor = redactor;
        this.objectMapper = objectMapper;
        this.conversationService = conversationService;
        this.metrics = metrics;
    }

    public AnswerResponse answer(PayFlowPrincipal principal, UUID conversationId, String userContent,
                                 String resourceType, String resourceReference) {
        return answer(principal, conversationId, userContent, resourceType, resourceReference, null);
    }

    public AnswerResponse answer(PayFlowPrincipal principal, UUID conversationId, String userContent,
                                 String resourceType, String resourceReference, AgentProgressListener listener) {
        if (!properties.isEnabled()) {
            throw PayFlowException.unprocessable(ErrorCode.AI_UNAVAILABLE, "AI Copilot is currently disabled");
        }
        if (userContent == null || userContent.isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Message content is required");
        }

        AiConversation conversation = conversationService.requireOwned(principal, conversationId);
        metrics.recordRequest();
        long startNanos = System.nanoTime();

        AiProvider requested = AiProvider.fromString(properties.getDefaultProvider()).orElse(null);
        ResolvedCredential resolved = credentialProvider.resolve(principal.userId(), requested)
                .orElseThrow(() -> PayFlowException.unprocessable(
                        ErrorCode.AI_KEY_REQUIRED, "Connect an AI provider to use Copilot"));
        AiProvider provider = resolved.credential().provider();
        String model = modelFor(provider);

        EvidenceBundle evidence = gatherEvidence(principal, userContent, resourceType, resourceReference, listener);

        // Redact the evidence as a STRUCTURED tree (preserves JSON shape; redacts secret
        // values by key and pattern) before it is ever serialized into the prompt. This is
        // the gate between retrieved data and the model.
        Object redactedStructure = redactor.redactJson(objectMapper.convertValue(evidence.structure(), Object.class));
        String evidenceBlock = toJson(redactedStructure);
        List<LlmMessage> history = toLlmHistory(
                conversationService.history(conversation.getId(), properties.getMaxHistoryMessages()));
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
        LlmRequest request = promptBuilder.build(provider, model, history, evidenceBlock, userContent, timeout);

        LlmResponse llmResponse;
        try {
            llmResponse = llmClientFactory.forProvider(provider).complete(resolved.credential(), request);
        } catch (RuntimeException ex) {
            metrics.recordFailure();
            // Never leak the key or provider internals in the surfaced message.
            log.warn("LLM completion failed for provider {}: {}", provider, ex.getClass().getSimpleName());
            throw PayFlowException.unprocessable(ErrorCode.AI_UNAVAILABLE,
                    "The AI provider could not be reached. Please try again.");
        }

        String answerText = llmResponse == null || llmResponse.content() == null
                ? "" : llmResponse.content();

        // Persist the turn (user message, assistant message, tool executions).
        conversationService.saveMessage(conversation.getId(), MessageRole.USER, userContent, null);
        String metadataJson = buildMetadata(evidence);
        AiMessage assistantMessage = conversationService.saveMessage(
                conversation.getId(), MessageRole.ASSISTANT, answerText, metadataJson);
        conversationService.saveToolExecutions(conversation.getId(), assistantMessage.getId(), evidence.toolResults());
        conversationService.touch(conversation);

        recordMetrics(evidence, llmResponse, startNanos);

        return new AnswerResponse(assistantMessage.getId(), conversation.getId(), answerText,
                evidence.evidence(), evidence.confidence(), evidence.warnings(), evidence.toolCalls());
    }

    private EvidenceBundle gatherEvidence(PayFlowPrincipal principal, String userContent,
                                          String resourceType, String resourceReference,
                                          AgentProgressListener listener) {
        String paymentReference = resolvePaymentReference(resourceType, resourceReference, userContent);
        if (paymentReference != null) {
            InvestigationResult result = investigationService.investigate(principal, paymentReference,
                    toolResult -> emit(listener, toolResult));
            List<EvidenceItem> items = new ArrayList<>();
            for (EvidenceRef ref : result.evidenceRefs()) {
                items.add(new EvidenceItem(ref.source(), ref.resourceType(), ref.resourceId(), ref.verified()));
            }
            Map<String, Object> structure = new LinkedHashMap<>();
            structure.put("paymentReference", result.paymentReference());
            structure.put("found", result.found());
            structure.put("facts", result.facts());
            structure.put("inferences", result.inferences());
            structure.put("missing", result.missing());
            structure.put("warnings", result.warnings());
            structure.put("confidence", result.confidence().name());
            return new EvidenceBundle(result.toolResults(), items, result.confidence().name(),
                    result.warnings(), toToolCalls(result.toolResults()), structure);
        }
        return gatherGeneralContext(principal, listener);
    }

    /** Non-payment questions: capability + idempotency context, capped by max tool calls. */
    private EvidenceBundle gatherGeneralContext(PayFlowPrincipal principal, AgentProgressListener listener) {
        int budget = Math.max(1, properties.getMaxToolCalls());
        List<ToolResult> executed = new ArrayList<>();
        Map<String, Object> structure = new LinkedHashMap<>();

        ToolResult capability = runRegistryTool(GetServiceCapabilityTool.NAME, principal, Map.of(), listener);
        if (capability != null && executed.size() < budget) {
            executed.add(capability);
            structure.put("capabilities", capability.data());
        }
        ToolResult idempotency = runRegistryTool(ExplainIdempotencyContractTool.NAME, principal, Map.of(), listener);
        if (idempotency != null && executed.size() < budget) {
            executed.add(idempotency);
            structure.put("idempotency", idempotency.data());
        }

        List<EvidenceItem> items = List.of(
                new EvidenceItem("ai-agent-service", "capability", "all", true),
                new EvidenceItem("ai-agent-service", "idempotency-contract", "static", true));
        return new EvidenceBundle(executed, items, "MEDIUM", List.of(), toToolCalls(executed), structure);
    }

    private ToolResult runRegistryTool(String name, PayFlowPrincipal principal, Map<String, String> args,
                                       AgentProgressListener listener) {
        var tool = toolRegistry.byName(name);
        if (tool == null) {
            return null;
        }
        ToolResult result = tool.run(ToolContext.of(principal, args));
        emit(listener, result);
        return result;
    }

    private static void emit(AgentProgressListener listener, ToolResult result) {
        if (listener != null && result != null) {
            listener.onTool(new ToolCall(result.toolName(), result.status().name()));
        }
    }

    private static List<ToolCall> toToolCalls(List<ToolResult> results) {
        List<ToolCall> calls = new ArrayList<>(results.size());
        for (ToolResult result : results) {
            calls.add(new ToolCall(result.toolName(), result.status().name()));
        }
        return calls;
    }

    private String resolvePaymentReference(String resourceType, String resourceReference, String userContent) {
        if ("PAYMENT".equalsIgnoreCase(resourceType) && resourceReference != null && !resourceReference.isBlank()) {
            return resourceReference.trim();
        }
        if (resourceReference != null && resourceReference.startsWith("pay_")) {
            return resourceReference.trim();
        }
        Matcher matcher = PAYMENT_REF.matcher(userContent);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void recordMetrics(EvidenceBundle evidence, LlmResponse llmResponse, long startNanos) {
        metrics.recordToolCalls(evidence.toolResults().size());
        long failures = evidence.toolResults().stream()
                .filter(r -> r.status() == ToolStatus.FAILURE || r.status() == ToolStatus.TIMEOUT)
                .count();
        metrics.recordToolFailures(failures);
        if (llmResponse != null) {
            metrics.recordInputTokens(llmResponse.inputTokens());
            metrics.recordOutputTokens(llmResponse.outputTokens());
        }
        metrics.recordSuccess();
        metrics.recordLatency(Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private String buildMetadata(EvidenceBundle evidence) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evidence", evidence.evidence());
        metadata.put("confidence", evidence.confidence());
        metadata.put("warnings", evidence.warnings());
        metadata.put("toolCalls", evidence.toolCalls());
        metadata.put("promptVersion", SystemPrompt.VERSION);
        return toJson(metadata);
    }

    private List<LlmMessage> toLlmHistory(List<AiMessage> messages) {
        List<LlmMessage> history = new ArrayList<>();
        for (AiMessage message : messages) {
            LlmMessage.Role role = switch (message.getRole()) {
                case USER -> LlmMessage.Role.USER;
                case ASSISTANT -> LlmMessage.Role.ASSISTANT;
                case SYSTEM -> LlmMessage.Role.SYSTEM;
            };
            // Only prior user/assistant turns are replayed; redact defensively.
            if (role != LlmMessage.Role.SYSTEM) {
                history.add(new LlmMessage(role, redactor.redact(message.getContent())));
            }
        }
        return history;
    }

    private String modelFor(AiProvider provider) {
        String configured = properties.getDefaultModel();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return switch (provider) {
            case ANTHROPIC -> "claude-3-5-sonnet-latest";
            case OPENAI -> "gpt-4o-mini";
            case GEMINI -> "gemini-1.5-pro";
            case XAI -> "grok-2-latest";
            case CUSTOM_OPENAI_COMPATIBLE -> "gpt-4o-mini";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize evidence/metadata to JSON: {}", ex.getOriginalMessage());
            return "{}";
        }
    }

    /** Internal carrier for gathered evidence, kept out of the public API. */
    private record EvidenceBundle(
            List<ToolResult> toolResults,
            List<EvidenceItem> evidence,
            String confidence,
            List<String> warnings,
            List<ToolCall> toolCalls,
            Object structure) {
    }
}

package com.payflow.ai.llm;

import com.payflow.ai.llm.provider.AnthropicLlmClient;
import com.payflow.ai.llm.provider.GeminiLlmClient;
import com.payflow.ai.llm.provider.OpenAiLlmClient;
import com.payflow.ai.llm.provider.XAiLlmClient;
import com.payflow.common.error.PayFlowException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientFactoryTest {

    private LlmClientFactory factory() {
        OpenAiLlmClient openAi = new OpenAiLlmClient();
        List<LlmClient> clients = List.of(new AnthropicLlmClient(), openAi, new XAiLlmClient(), new GeminiLlmClient());
        return new LlmClientFactory(clients, openAi);
    }

    @Test
    void returnsClientMatchingProvider() {
        LlmClientFactory factory = factory();
        assertThat(factory.forProvider(AiProvider.ANTHROPIC).provider()).isEqualTo(AiProvider.ANTHROPIC);
        assertThat(factory.forProvider(AiProvider.OPENAI).provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(factory.forProvider(AiProvider.XAI).provider()).isEqualTo(AiProvider.XAI);
        assertThat(factory.forProvider(AiProvider.GEMINI).provider()).isEqualTo(AiProvider.GEMINI);
    }

    @Test
    void customProviderIsServedByOpenAiCompatibleClient() {
        assertThat(factory().forProvider(AiProvider.CUSTOM_OPENAI_COMPATIBLE)).isInstanceOf(OpenAiLlmClient.class);
    }

    @Test
    void throwsWhenProviderIsNull() {
        assertThatThrownBy(() -> factory().forProvider(null)).isInstanceOf(PayFlowException.class);
    }

    @Test
    void fakeClientReturnsCannedResponseWithoutNetwork() {
        FakeLlmClient fake = new FakeLlmClient(AiProvider.ANTHROPIC);
        LlmRequest request = new LlmRequest(AiProvider.ANTHROPIC, "fake-model", "system",
                List.of(LlmMessage.user("hi")), 256, Duration.ofSeconds(5));

        LlmResponse response = fake.complete(new LlmCredential(AiProvider.ANTHROPIC, "sk-ant-x", null), request);

        assertThat(response.content()).isEqualTo("canned test answer");
        assertThat(response.inputTokens()).isEqualTo(10);
    }
}

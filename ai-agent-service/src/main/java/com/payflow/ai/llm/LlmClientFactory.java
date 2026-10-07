package com.payflow.ai.llm;

import com.payflow.ai.llm.provider.OpenAiLlmClient;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the {@link LlmClient} for a provider. The OpenAI-compatible client
 * additionally backs {@link AiProvider#CUSTOM_OPENAI_COMPATIBLE}, which carries
 * its base URL on the credential.
 */
@Component
public class LlmClientFactory {

    private final Map<AiProvider, LlmClient> clientsByProvider;

    public LlmClientFactory(List<LlmClient> clients, OpenAiLlmClient openAiLlmClient) {
        Map<AiProvider, LlmClient> map = new EnumMap<>(AiProvider.class);
        for (LlmClient client : clients) {
            map.putIfAbsent(client.provider(), client);
        }
        map.putIfAbsent(AiProvider.CUSTOM_OPENAI_COMPATIBLE, openAiLlmClient);
        this.clientsByProvider = map;
    }

    public LlmClient forProvider(AiProvider provider) {
        LlmClient client = provider == null ? null : clientsByProvider.get(provider);
        if (client == null) {
            throw PayFlowException.unprocessable(ErrorCode.AI_UNAVAILABLE,
                    "No LLM client is available for provider " + provider);
        }
        return client;
    }
}

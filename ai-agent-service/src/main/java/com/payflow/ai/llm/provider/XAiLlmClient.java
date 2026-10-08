package com.payflow.ai.llm.provider;

import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmClient;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import org.springframework.stereotype.Component;

/**
 * xAI (Grok) client. xAI exposes an OpenAI-compatible Chat Completions API, so
 * this delegates to the shared exchange against the fixed xAI base URL.
 */
@Component
public class XAiLlmClient implements LlmClient {

    static final String BASE_URL = "https://api.x.ai/v1";

    @Override
    public AiProvider provider() {
        return AiProvider.XAI;
    }

    @Override
    public LlmResponse complete(LlmCredential credential, LlmRequest request) {
        return OpenAiCompatibleCompletions.complete(AiProvider.XAI, BASE_URL, credential, request);
    }
}

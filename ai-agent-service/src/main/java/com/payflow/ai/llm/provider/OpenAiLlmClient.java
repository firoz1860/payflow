package com.payflow.ai.llm.provider;

import com.payflow.ai.llm.AiProvider;
import com.payflow.ai.llm.LlmClient;
import com.payflow.ai.llm.LlmCredential;
import com.payflow.ai.llm.LlmRequest;
import com.payflow.ai.llm.LlmResponse;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completions client. Also serves custom OpenAI-compatible endpoints:
 * when the credential carries a base URL it is used (already SSRF-validated at
 * credential-store time), otherwise the public OpenAI API is targeted.
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    @Override
    public AiProvider provider() {
        return AiProvider.OPENAI;
    }

    @Override
    public LlmResponse complete(LlmCredential credential, LlmRequest request) {
        String baseUrl = credential.baseUrl() != null && !credential.baseUrl().isBlank()
                ? credential.baseUrl() : DEFAULT_BASE_URL;
        return OpenAiCompatibleCompletions.complete(request.provider(), baseUrl, credential, request);
    }
}

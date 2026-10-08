package com.payflow.ai.llm;

/**
 * A resolved LLM credential held in memory only — never logged, never persisted,
 * never returned to the browser. {@code baseUrl} is meaningful only for
 * {@link AiProvider#CUSTOM_OPENAI_COMPATIBLE}. The {@link #toString()} override
 * guarantees the key never leaks through logging or an exception that happens to
 * print the object.
 */
public record LlmCredential(AiProvider provider, String apiKey, String baseUrl) {

    @Override
    public String toString() {
        return "LlmCredential[provider=" + provider + ", baseUrl=" + baseUrl + ", apiKey=***]";
    }
}

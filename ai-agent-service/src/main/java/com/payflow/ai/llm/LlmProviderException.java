package com.payflow.ai.llm;

/**
 * A failure returned by (or while talking to) a provider endpoint. It carries the
 * HTTP status and the provider only — never the API key, the authorization
 * header, or a request URI. The underlying client exception is deliberately not
 * chained as a cause, because some providers embed the key in the request URI and
 * that URI can appear in the cause's message/stack trace.
 */
public class LlmProviderException extends LlmException {

    private final int statusCode;
    private final AiProvider provider;

    public LlmProviderException(String message, int statusCode, AiProvider provider) {
        super(message);
        this.statusCode = statusCode;
        this.provider = provider;
    }

    public int statusCode() {
        return statusCode;
    }

    public AiProvider provider() {
        return provider;
    }
}

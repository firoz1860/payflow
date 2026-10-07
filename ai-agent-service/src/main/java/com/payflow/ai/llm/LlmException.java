package com.payflow.ai.llm;

/**
 * Base failure for the LLM layer. Messages and fields must never carry an API
 * key, an authorization header, or a request URI that embeds a key.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}

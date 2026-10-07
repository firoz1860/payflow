package com.payflow.ai.llm;

/**
 * A single provider-neutral chat message. The orchestration layer builds these
 * from the conversation and the deterministic evidence structure; the content is
 * always expected to have passed through redaction before it reaches a provider.
 */
public record LlmMessage(Role role, String content) {

    public enum Role { SYSTEM, USER, ASSISTANT }

    public static LlmMessage system(String content) {
        return new LlmMessage(Role.SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content);
    }
}

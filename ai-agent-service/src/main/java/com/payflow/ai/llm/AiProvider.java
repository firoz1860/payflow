package com.payflow.ai.llm;

import java.util.Optional;

/** Supported LLM providers for BYOK. CUSTOM is any OpenAI-compatible endpoint. */
public enum AiProvider {
    ANTHROPIC("sk-ant-"),
    OPENAI("sk-"),
    GEMINI(""),
    XAI("xai-"),
    CUSTOM_OPENAI_COMPATIBLE("");

    private final String keyHint;

    AiProvider(String keyHint) {
        this.keyHint = keyHint;
    }

    /** A non-authoritative hint of the usual key prefix (used only for UX, never validation). */
    public String keyHint() {
        return keyHint;
    }

    public static Optional<AiProvider> fromString(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AiProvider.valueOf(value.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}

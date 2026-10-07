package com.payflow.ai.llm;

import java.time.Duration;
import java.util.List;

/**
 * A provider-neutral completion request. The caller selects the provider/model;
 * {@code systemPrompt} is kept separate from {@code messages} so providers that
 * carry a dedicated system field (Anthropic, Gemini) can map it natively.
 */
public record LlmRequest(
        AiProvider provider,
        String model,
        String systemPrompt,
        List<LlmMessage> messages,
        int maxTokens,
        Duration timeout) {
}

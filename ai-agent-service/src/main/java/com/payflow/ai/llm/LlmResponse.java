package com.payflow.ai.llm;

/**
 * A provider-neutral completion result. Token counts are nullable because not
 * every provider returns usage metadata on every call.
 */
public record LlmResponse(String content, Integer inputTokens, Integer outputTokens, String model) {
}

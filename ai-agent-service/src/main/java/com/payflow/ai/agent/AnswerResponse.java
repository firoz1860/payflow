package com.payflow.ai.agent;

import java.util.List;
import java.util.UUID;

/**
 * The Copilot answer contract:
 * {@code { messageId, conversationId, answer, evidence[], confidence, warnings[], toolCalls[] }}.
 * {@code confidence} is one of HIGH|MEDIUM|LOW|UNKNOWN and is decided deterministically
 * by the server, not the model.
 */
public record AnswerResponse(
        UUID messageId,
        UUID conversationId,
        String answer,
        List<EvidenceItem> evidence,
        String confidence,
        List<String> warnings,
        List<ToolCall> toolCalls) {
}

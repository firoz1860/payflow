package com.payflow.ai.conversation;

/** Role of a stored message. Matches the ai_messages role check constraint. */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

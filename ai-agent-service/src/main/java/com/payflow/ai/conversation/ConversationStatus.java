package com.payflow.ai.conversation;

/** Lifecycle of a conversation. DELETED is a soft-delete marker. */
public enum ConversationStatus {
    ACTIVE,
    ARCHIVED,
    DELETED
}

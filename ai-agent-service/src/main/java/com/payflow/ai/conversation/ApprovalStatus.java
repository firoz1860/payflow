package com.payflow.ai.conversation;

/**
 * Lifecycle of an approval request. The data model exists in V1 (human-in-the-loop
 * scaffolding) but no action executes yet.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED,
    EXECUTED,
    FAILED
}

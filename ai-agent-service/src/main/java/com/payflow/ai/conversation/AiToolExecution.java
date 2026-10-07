package com.payflow.ai.conversation;

import com.payflow.ai.tools.ToolStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code ai_tool_executions}. Stores only a safe, redacted {@code resourceRef}
 * (e.g. {@code payment:pay_xxx}) and the outcome — never raw tool output.
 */
@Entity
@Table(name = "ai_tool_executions")
public class AiToolExecution {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ToolStatus status;

    @Column(name = "resource_ref", length = 128)
    private String resourceRef;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiToolExecution() {
    }

    public AiToolExecution(UUID conversationId, UUID messageId, String toolName, ToolStatus status,
                           String resourceRef, Integer latencyMs) {
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.toolName = toolName;
        this.status = status;
        this.resourceRef = truncate(resourceRef, 128);
        this.latencyMs = latencyMs;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolStatus getStatus() {
        return status;
    }

    public String getResourceRef() {
        return resourceRef;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

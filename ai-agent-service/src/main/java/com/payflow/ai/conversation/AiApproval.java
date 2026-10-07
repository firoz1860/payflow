package com.payflow.ai.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code ai_approvals}. The human-in-the-loop approval model exists in V1 so
 * the schema and entity are stable, but no action executes yet (V1 is read-only).
 * {@code sanitizedParams} is already-redacted JSON — never raw secrets.
 */
@Entity
@Table(name = "ai_approvals")
public class AiApproval {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "requested_action", nullable = false, length = 64)
    private String requestedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_params", nullable = false, columnDefinition = "jsonb")
    private String sanitizedParams;

    @Column(name = "requesting_user_id", nullable = false)
    private UUID requestingUserId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "required_permission", nullable = false, length = 64)
    private String requiredPermission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "approving_user_id")
    private UUID approvingUserId;

    @Column(name = "execution_result_ref", length = 128)
    private String executionResultRef;

    protected AiApproval() {
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ApprovalStatus.PENDING;
        }
    }

    public UUID getId() {
        return id;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}

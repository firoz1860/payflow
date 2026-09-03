package com.payflow.auth.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "one_time_tokens", indexes = {
        @Index(name = "idx_one_time_tokens_user", columnList = "user_id")
})
public class OneTimeToken {
    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private Purpose purpose;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected OneTimeToken() {
    }
    public OneTimeToken(String tokenHash, UUID userId, Purpose purpose, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }
    public boolean isUsable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }
    public void consume() {
        this.consumedAt = Instant.now();
    }
    public UUID getUserId() {
        return userId;
    }
    public Purpose getPurpose() {
        return purpose;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
}

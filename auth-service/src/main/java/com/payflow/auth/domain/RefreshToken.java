package com.payflow.auth.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id")
})
public class RefreshToken {
    public enum Status { ACTIVE, ROTATED, REVOKED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
    @Column(name = "status", nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.ACTIVE;
    @Column(name = "user_agent", length = 255)
    private String userAgent;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "revoked_at")
    private Instant revokedAt;
    protected RefreshToken() {
    }
    public RefreshToken(String tokenHash, UUID userId, UUID familyId, Instant expiresAt,
                        String userAgent, String ipAddress) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent, 255);
        this.ipAddress = truncate(ipAddress, 64);
    }
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
    public boolean isUsable() {
        return status == Status.ACTIVE && expiresAt.isAfter(Instant.now());
    }
    public void markRotated() {
        this.status = Status.ROTATED;
    }
    public void revoke() {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }
    public UUID getId() {
        return id;
    }
    public String getTokenHash() {
        return tokenHash;
    }
    public UUID getUserId() {
        return userId;
    }
    public UUID getFamilyId() {
        return familyId;
    }
    public Status getStatus() {
        return status;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}

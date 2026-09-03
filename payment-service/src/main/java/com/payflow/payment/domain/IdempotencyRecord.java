package com.payflow.payment.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_merchant_key",
                columnNames = {"merchant_id", "idempotency_key"}))
public class IdempotencyRecord {
    public enum Status { IN_PROGRESS, COMPLETED, FAILED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;
    @Column(name = "endpoint", nullable = false, length = 128)
    private String endpoint;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.IN_PROGRESS;
    @Column(name = "resource_id", length = 64)
    private String resourceId;
    @Column(name = "response_code")
    private Integer responseCode;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    protected IdempotencyRecord() {
    }
    public IdempotencyRecord(UUID merchantId, String idempotencyKey, String endpoint,
                             String requestHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.endpoint = endpoint;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
    }
    public void complete(String resourceId, int responseCode, String responseBody) {
        this.resourceId = resourceId;
        this.responseCode = responseCode;
        this.responseBody = responseBody;
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }
    public void fail() {
        this.status = Status.FAILED;
        this.completedAt = Instant.now();
    }
    public boolean isStale(java.time.Duration threshold) {
        return status == Status.IN_PROGRESS && createdAt.isBefore(Instant.now().minus(threshold));
    }
    public UUID getId() {
        return id;
    }
    public UUID getMerchantId() {
        return merchantId;
    }
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    public String getEndpoint() {
        return endpoint;
    }
    public String getRequestHash() {
        return requestHash;
    }
    public Status getStatus() {
        return status;
    }
    public String getResourceId() {
        return resourceId;
    }
    public Integer getResponseCode() {
        return responseCode;
    }
    public String getResponseBody() {
        return responseBody;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void renew(String requestHash, Instant expiresAt) {
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
        this.createdAt = Instant.now();
        this.completedAt = null;
        this.responseBody = null;
        this.responseCode = null;
        this.resourceId = null;
        this.expiresAt = expiresAt;
    }
}

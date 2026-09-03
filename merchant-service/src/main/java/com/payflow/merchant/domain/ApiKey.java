package com.payflow.merchant.domain;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_keys_merchant", columnList = "merchant_id"),
        @Index(name = "idx_api_keys_lookup", columnList = "lookup_hash", unique = true)
})
public class ApiKey {
    public enum Environment { TEST, LIVE }
    public enum KeyType {
        PUBLISHABLE,
        SECRET
    }
    public enum Status { ACTIVE, REVOKED, EXPIRED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "key_id", nullable = false, unique = true, length = 64)
    private String keyId;
    @Column(name = "lookup_hash", nullable = false, unique = true, length = 64)
    private String lookupHash;
    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;
    @Column(name = "masked_key", nullable = false, length = 64)
    private String maskedKey;
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 8)
    private Environment environment;
    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 16)
    private KeyType keyType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.ACTIVE;
    @Column(name = "label", length = 120)
    private String label;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "api_key_id"))
    @Column(name = "scope", nullable = false, length = 64)
    private Set<String> scopes = new LinkedHashSet<>();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_by")
    private UUID createdBy;
    protected ApiKey() {
    }
    public ApiKey(String keyId, String lookupHash, String secretHash, String maskedKey,
                  UUID merchantId, Environment environment, KeyType keyType,
                  String label, Set<String> scopes, Instant expiresAt, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.keyId = keyId;
        this.lookupHash = lookupHash;
        this.secretHash = secretHash;
        this.maskedKey = maskedKey;
        this.merchantId = merchantId;
        this.environment = environment;
        this.keyType = keyType;
        this.label = label;
        this.scopes = new LinkedHashSet<>(scopes);
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }
    public boolean isUsable() {
        return status == Status.ACTIVE && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }
    public void revoke() {
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
    }
    public void touchLastUsed(Instant when) {
        this.lastUsedAt = when;
    }
    public UUID getId() {
        return id;
    }
    public String getKeyId() {
        return keyId;
    }
    public String getLookupHash() {
        return lookupHash;
    }
    public String getSecretHash() {
        return secretHash;
    }
    public String getMaskedKey() {
        return maskedKey;
    }
    public UUID getMerchantId() {
        return merchantId;
    }
    public Environment getEnvironment() {
        return environment;
    }
    public KeyType getKeyType() {
        return keyType;
    }
    public Status getStatus() {
        return status;
    }
    public String getLabel() {
        return label;
    }
    public Set<String> getScopes() {
        return scopes;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}

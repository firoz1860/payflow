package com.payflow.provider.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "provider_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_event",
                columnNames = {"provider", "provider_event_id"}))
public class ProviderEvent {
    public enum ProcessingStatus { RECEIVED, PROCESSED, FAILED, IGNORED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;
    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;
    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 16)
    private ProcessingStatus processingStatus = ProcessingStatus.RECEIVED;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
    @Column(name = "processed_at")
    private Instant processedAt;
    protected ProviderEvent() {
    }
    public ProviderEvent(String provider, String providerEventId, String eventType,
                         String providerPaymentId, String payload) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.providerPaymentId = providerPaymentId;
        this.payload = payload;
    }
    public void markProcessed() {
        this.processingStatus = ProcessingStatus.PROCESSED;
        this.processedAt = Instant.now();
    }
    public void markFailed(String reason) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.failureReason = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), 1000));
        this.processedAt = Instant.now();
    }
    public void markIgnored(String reason) {
        this.processingStatus = ProcessingStatus.IGNORED;
        this.failureReason = reason;
        this.processedAt = Instant.now();
    }
    public UUID getId() {
        return id;
    }
    public String getProvider() {
        return provider;
    }
    public String getProviderEventId() {
        return providerEventId;
    }
    public String getEventType() {
        return eventType;
    }
    public String getProviderPaymentId() {
        return providerPaymentId;
    }
    public String getPayload() {
        return payload;
    }
    public ProcessingStatus getProcessingStatus() {
        return processingStatus;
    }
    public Instant getReceivedAt() {
        return receivedAt;
    }
}

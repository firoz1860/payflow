package com.payflow.common.outbox;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    public enum Status { PENDING, PUBLISHED, FAILED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;
    @Column(name = "partition_key", nullable = false, length = 128)
    private String partitionKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "last_error", length = 1000)
    private String lastError;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    protected OutboxEvent() {
    }
    public OutboxEvent(String aggregateType, String aggregateId, String eventType,
                       String partitionKey, String payload, String correlationId) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.correlationId = correlationId;
    }
    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (this.attempts >= 10) {
            this.status = Status.FAILED;
        }
    }
    public UUID getId() {
        return id;
    }
    public String getAggregateType() {
        return aggregateType;
    }
    public String getAggregateId() {
        return aggregateId;
    }
    public String getEventType() {
        return eventType;
    }
    public String getPartitionKey() {
        return partitionKey;
    }
    public String getPayload() {
        return payload;
    }
    public Status getStatus() {
        return status;
    }
    public int getAttempts() {
        return attempts;
    }
    public String getCorrelationId() {
        return correlationId;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getPublishedAt() {
        return publishedAt;
    }
}

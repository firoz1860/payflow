package com.payflow.ledger.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
@Entity
@Table(name = "ledger_postings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_posting_source",
                columnNames = {"source_type", "source_id"}))
public class LedgerPosting {
    public enum SourceType { PAYMENT, REFUND, FEE, SETTLEMENT, ADJUSTMENT, REVERSAL }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private SourceType sourceType;
    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;
    @Column(name = "merchant_id", length = 64)
    private String merchantId;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit;
    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit;
    @Column(name = "description", length = 255)
    private String description;
    @Column(name = "reverses_posting_id")
    private UUID reversesPostingId;
    @Column(name = "correlation_id", length = 64)
    private String correlationId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected LedgerPosting() {
    }
    public LedgerPosting(SourceType sourceType, String sourceId, String merchantId, String currency,
                         BigDecimal totalDebit, BigDecimal totalCredit, String description,
                         String correlationId) {
        this.id = UUID.randomUUID();
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.merchantId = merchantId;
        this.currency = currency;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.description = description;
        this.correlationId = correlationId;
    }
    public void markReverses(UUID postingId) {
        this.reversesPostingId = postingId;
    }
    public UUID getId() {
        return id;
    }
    public SourceType getSourceType() {
        return sourceType;
    }
    public String getSourceId() {
        return sourceId;
    }
    public String getMerchantId() {
        return merchantId;
    }
    public String getCurrency() {
        return currency;
    }
    public BigDecimal getTotalDebit() {
        return totalDebit;
    }
    public BigDecimal getTotalCredit() {
        return totalCredit;
    }
    public String getDescription() {
        return description;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}

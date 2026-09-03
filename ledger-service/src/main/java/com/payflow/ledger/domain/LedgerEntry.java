package com.payflow.ledger.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_entries_account", columnList = "account_id,created_at"),
        @Index(name = "idx_entries_posting", columnList = "posting_id")
})
public class LedgerEntry {
    public enum EntryType { DEBIT, CREDIT }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "posting_id", nullable = false, updatable = false)
    private UUID postingId;
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 8)
    private EntryType entryType;
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;
    @Column(name = "description", updatable = false, length = 255)
    private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected LedgerEntry() {
    }
    public LedgerEntry(UUID postingId, UUID accountId, EntryType entryType,
                       BigDecimal amount, String currency, String description) {
        this.id = UUID.randomUUID();
        this.postingId = postingId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }
    public UUID getId() {
        return id;
    }
    public UUID getPostingId() {
        return postingId;
    }
    public UUID getAccountId() {
        return accountId;
    }
    public EntryType getEntryType() {
        return entryType;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getCurrency() {
        return currency;
    }
    public String getDescription() {
        return description;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}

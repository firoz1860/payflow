package com.payflow.ledger.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
@Entity
@Table(name = "ledger_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ledger_account_identity",
                columnNames = {"owner_type", "owner_id", "account_type", "currency"}))
public class LedgerAccount {
    public enum OwnerType { PLATFORM, MERCHANT, CUSTOMER, PROVIDER }
    public enum AccountType {
        PAYMENT_CLEARING(Normal.DEBIT),
        PLATFORM_CASH(Normal.DEBIT),
        MERCHANT_PAYABLE(Normal.CREDIT),
        FEE_REVENUE(Normal.CREDIT),
        TAX_PAYABLE(Normal.CREDIT),
        REFUND_CLEARING(Normal.DEBIT),
        MERCHANT_RESERVE(Normal.CREDIT);
        public enum Normal { DEBIT, CREDIT }
        private final Normal normalBalance;
        AccountType(Normal normalBalance) {
            this.normalBalance = normalBalance;
        }
        public Normal normalBalance() {
            return normalBalance;
        }
    }
    public enum Status { ACTIVE, FROZEN, CLOSED }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private OwnerType ownerType;
    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType;
    @JdbcTypeCode(Types.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected LedgerAccount() {
    }
    public LedgerAccount(OwnerType ownerType, String ownerId, AccountType accountType, String currency) {
        this.id = UUID.randomUUID();
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.accountType = accountType;
        this.currency = currency.toUpperCase();
    }
    public boolean acceptsPostings() {
        return status == Status.ACTIVE;
    }
    public UUID getId() {
        return id;
    }
    public OwnerType getOwnerType() {
        return ownerType;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public AccountType getAccountType() {
        return accountType;
    }
    public String getCurrency() {
        return currency;
    }
    public Status getStatus() {
        return status;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}

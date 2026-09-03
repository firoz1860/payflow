package com.payflow.payment.domain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "payment_attempts", indexes = {
        @Index(name = "idx_attempts_payment", columnList = "payment_id,attempt_number"),
        @Index(name = "idx_attempts_provider_ref", columnList = "provider,provider_payment_id")
})
public class PaymentAttempt {
    public enum Status { INITIATED, PENDING, SUCCEEDED, FAILED, CANCELLED }
    public enum PaymentMethod { CARD, UPI, QR, NET_BANKING, WALLET }
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;
    @Column(name = "provider", nullable = false, length = 32)
    private String provider;
    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    private PaymentMethod paymentMethod;
    @Column(name = "payment_method_token", length = 128)
    private String paymentMethodToken;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", length = 4)
    private String cardLast4;
    @Column(name = "card_network", length = 32)
    private String cardNetwork;
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.INITIATED;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "failure_message", length = 500)
    private String failureMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    protected PaymentAttempt() {
    }
    public PaymentAttempt(UUID paymentId, int attemptNumber, String provider,
                          BigDecimal amount, String currency, PaymentMethod paymentMethod) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
    }
    public void markPending(String providerPaymentId) {
        this.providerPaymentId = providerPaymentId;
        this.status = Status.PENDING;
        touch();
    }
    public void markSucceeded(String providerPaymentId) {
        if (providerPaymentId != null) {
            this.providerPaymentId = providerPaymentId;
        }
        this.status = Status.SUCCEEDED;
        touch();
    }
    public void markFailed(String failureCode, String failureMessage) {
        this.status = Status.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage == null ? null
                : failureMessage.substring(0, Math.min(failureMessage.length(), 500));
        touch();
    }
    public void attachInstrument(String token, String last4, String network, PaymentMethod method) {
        this.paymentMethodToken = token;
        this.cardLast4 = last4;
        this.cardNetwork = network;
        if (method != null) {
            this.paymentMethod = method;
        }
        touch();
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }
    public UUID getId() {
        return id;
    }
    public UUID getPaymentId() {
        return paymentId;
    }
    public int getAttemptNumber() {
        return attemptNumber;
    }
    public String getProvider() {
        return provider;
    }
    public String getProviderPaymentId() {
        return providerPaymentId;
    }
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
    public String getCardLast4() {
        return cardLast4;
    }
    public String getCardNetwork() {
        return cardNetwork;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getCurrency() {
        return currency;
    }
    public Status getStatus() {
        return status;
    }
    public String getFailureCode() {
        return failureCode;
    }
    public String getFailureMessage() {
        return failureMessage;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
}

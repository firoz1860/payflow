package com.payflow.payment.domain;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_merchant", columnList = "merchant_id,created_at"),
        @Index(name = "idx_payments_status", columnList = "status")
})
public class Payment {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "payment_reference", nullable = false, unique = true, length = 40)
    private String paymentReference;
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "merchant_order_id", length = 128)
    private String merchantOrderId;
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.CREATED;
    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 8)
    private Environment environment;
    @Column(name = "description", length = 500)
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    @Column(name = "provider", length = 32)
    private String provider;
    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;
    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;
    @Column(name = "qr_code_data", columnDefinition = "text")
    private String qrCodeData;
    @Column(name = "qr_code_image", columnDefinition = "text")
    private String qrCodeImage;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "failure_message", length = 500)
    private String failureMessage;
    @Column(name = "risk_decision", length = 16)
    private String riskDecision;
    @Column(name = "risk_score")
    private Integer riskScore;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "authorized_at")
    private Instant authorizedAt;
    @Column(name = "captured_at")
    private Instant capturedAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    public enum Environment { TEST, LIVE }
    protected Payment() {
    }
    public Payment(String paymentReference, UUID merchantId, UUID customerId, String merchantOrderId,
                   BigDecimal amount, String currency, Environment environment,
                   String description, String metadata, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.paymentReference = paymentReference;
        this.merchantId = merchantId;
        this.customerId = customerId;
        this.merchantOrderId = merchantOrderId;
        this.amount = amount;
        this.currency = currency.toUpperCase();
        this.environment = environment;
        this.description = description;
        this.metadata = metadata;
        this.expiresAt = expiresAt;
    }
    public void transitionTo(PaymentStatus target) {
        if (status == target) {
            return;   // idempotent: a duplicated webhook is a no-op, not an error
        }
        if (!status.canTransitionTo(target)) {
            throw PayFlowException.conflict(ErrorCode.CONFLICT,
                    "Illegal payment transition " + status + " -> " + target);
        }
        this.status = target;
        if (target == PaymentStatus.AUTHORIZED) {
            this.authorizedAt = Instant.now();
        }
        if (target == PaymentStatus.CAPTURED) {
            this.capturedAt = Instant.now();
        }
        touch();
    }
    public void attachProvider(String provider, String providerPaymentId, String checkoutUrl) {
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.checkoutUrl = checkoutUrl;
        touch();
    }
    public void attachQr(String qrCodeData, String qrCodeImage) {
        this.qrCodeData = qrCodeData;
        this.qrCodeImage = qrCodeImage;
        touch();
    }
    public void markFailed(String failureCode, String failureMessage) {
        this.failureCode = failureCode;
        this.failureMessage = truncate(failureMessage, 500);
        transitionTo(PaymentStatus.FAILED);
    }
    public void applyRiskDecision(String decision, Integer score) {
        this.riskDecision = decision;
        this.riskScore = score;
        touch();
    }
    public void registerRefund(BigDecimal refundAmount) {
        if (!status.isRefundable()) {
            throw PayFlowException.unprocessable(ErrorCode.PAYMENT_NOT_REFUNDABLE,
                    "Payment in status " + status + " cannot be refunded");
        }
        BigDecimal newTotal = refundedAmount.add(refundAmount);
        if (newTotal.compareTo(amount) > 0) {
            throw PayFlowException.unprocessable(ErrorCode.REFUND_AMOUNT_EXCEEDED,
                    "Refund of " + refundAmount + " exceeds the refundable balance of "
                            + refundableAmount());
        }
        this.refundedAmount = newTotal;
        this.status = newTotal.compareTo(amount) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        touch();
    }
    public BigDecimal refundableAmount() {
        return amount.subtract(refundedAmount);
    }
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
    private void touch() {
        this.updatedAt = Instant.now();
    }
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
    public UUID getId() {
        return id;
    }
    public String getPaymentReference() {
        return paymentReference;
    }
    public UUID getMerchantId() {
        return merchantId;
    }
    public UUID getCustomerId() {
        return customerId;
    }
    public String getMerchantOrderId() {
        return merchantOrderId;
    }
    public BigDecimal getAmount() {
        return amount;
    }
    public String getCurrency() {
        return currency;
    }
    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }
    public PaymentStatus getStatus() {
        return status;
    }
    public Environment getEnvironment() {
        return environment;
    }
    public String getDescription() {
        return description;
    }
    public String getMetadata() {
        return metadata;
    }
    public String getProvider() {
        return provider;
    }
    public String getProviderPaymentId() {
        return providerPaymentId;
    }
    public String getCheckoutUrl() {
        return checkoutUrl;
    }
    public String getQrCodeData() {
        return qrCodeData;
    }
    public String getQrCodeImage() {
        return qrCodeImage;
    }
    public String getFailureCode() {
        return failureCode;
    }
    public String getFailureMessage() {
        return failureMessage;
    }
    public String getRiskDecision() {
        return riskDecision;
    }
    public Integer getRiskScore() {
        return riskScore;
    }
    public Instant getCreatedAt() {
        return createdAt;
    }
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    public Instant getExpiresAt() {
        return expiresAt;
    }
    public Instant getCapturedAt() {
        return capturedAt;
    }
    public long getVersion() {
        return version;
    }
}

package com.payflow.payment.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public final class InternalAiPaymentDtos {
    private InternalAiPaymentDtos() {
    }
    public record PaymentEvidence(
            String paymentReference,
            String merchantId,
            String merchantOrderId,
            String customerId,
            BigDecimal amount,
            String currency,
            BigDecimal refundedAmount,
            BigDecimal refundableAmount,
            String status,
            String environment,
            String provider,
            String providerPaymentId,
            String failureCode,
            String failureMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant authorizedAt,
            Instant capturedAt,
            Instant expiresAt,
            List<AttemptEvidence> attempts
    ) {
    }
    public record AttemptEvidence(
            int attemptNumber,
            String provider,
            String providerPaymentId,
            String paymentMethod,
            String status,
            BigDecimal amount,
            String currency,
            String cardLast4,
            String cardNetwork,
            String failureCode,
            String failureMessage,
            Instant createdAt
    ) {
    }
}

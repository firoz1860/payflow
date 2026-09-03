package com.payflow.payment.dto;
import com.payflow.payment.domain.PaymentAttempt;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public final class PaymentDtos {
    private PaymentDtos() {
    }
    public record CreatePaymentRequest(
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than zero")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount,
            @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO 4217 code")
            String currency,
            @Size(max = 128) String merchantOrderId,
            UUID customerId,
            @Size(max = 500) String description,
            PaymentAttempt.PaymentMethod paymentMethod,
            @Size(max = 2000) String returnUrl,
            Map<String, String> metadata
    ) {
    }
    public record PaymentResponse(
            String paymentReference,
            String merchantOrderId,
            BigDecimal amount,
            String currency,
            BigDecimal refundedAmount,
            BigDecimal refundableAmount,
            String status,
            String environment,
            String description,
            String provider,
            String checkoutUrl,
            QrCode qrCode,
            String failureCode,
            String failureMessage,
            Map<String, String> metadata,
            List<AttemptResponse> attempts,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt
    ) {
    }
    /**
     * Present only for QR payments. {@code data} is the scannable payload (a
     * {@code upi://pay} intent or a provider QR string); {@code image} is a ready-to-render
     * {@code data:image/png;base64,...} URI (or a provider-hosted image URL).
     */
    public record QrCode(String data, String image) {
    }
    public record AttemptResponse(
            int attemptNumber,
            String provider,
            String paymentMethod,
            String status,
            BigDecimal amount,
            String cardLast4,
            String cardNetwork,
            String failureCode,
            String failureMessage,
            Instant createdAt
    ) {
    }
    public record CancelPaymentRequest(@Size(max = 255) String reason) {
    }
    public record RegisterRefundRequest(
            @NotNull String paymentReference,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull String refundReference
    ) {
    }
    public record PageResponse<T>(List<T> data, int page, int size, long totalElements, int totalPages) {
    }
}

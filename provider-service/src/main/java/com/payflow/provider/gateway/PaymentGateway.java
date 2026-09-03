package com.payflow.provider.gateway;
import java.math.BigDecimal;
import java.util.Map;
public interface PaymentGateway {
    String name();
    GatewayPayment createPayment(CreateGatewayPaymentCommand command);
    GatewayPayment getPayment(String providerPaymentId);
    GatewayRefund createRefund(RefundCommand command);
    boolean verifyWebhook(String rawPayload, String signature, String timestamp);
    GatewayEvent parseEvent(String rawPayload);
    record CreateGatewayPaymentCommand(
            String paymentReference,
            String merchantId,
            BigDecimal amount,
            String currency,
            String description,
            String paymentMethod,
            String returnUrl,
            String environment,
            String idempotencyKey,
            Map<String, String> metadata
    ) {
    }
    record RefundCommand(
            String providerPaymentId,
            String refundReference,
            BigDecimal amount,
            String currency,
            String reason,
            String idempotencyKey
    ) {
    }
    enum GatewayStatus { CREATED, PENDING, AUTHORIZED, CAPTURED, FAILED, CANCELLED }
    record GatewayPayment(
            String provider,
            String providerPaymentId,
            GatewayStatus status,
            BigDecimal amount,
            String currency,
            String checkoutUrl,
            String instrumentToken,
            String cardLast4,
            String cardNetwork,
            String paymentMethod,
            String failureCode,
            String failureMessage,
            String qrCodeData,
            String qrCodeImage
    ) {
    }
    record GatewayRefund(
            String provider,
            String providerRefundId,
            String providerPaymentId,
            String status,
            BigDecimal amount,
            String failureCode,
            String failureMessage
    ) {
    }
    record GatewayEvent(
            String provider,
            String providerEventId,
            String eventType,
            String providerPaymentId,
            GatewayStatus status,
            String instrumentToken,
            String cardLast4,
            String cardNetwork,
            String paymentMethod,
            String failureCode,
            String failureMessage
    ) {
    }
}

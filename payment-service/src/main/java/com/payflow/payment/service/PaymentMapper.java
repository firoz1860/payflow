package com.payflow.payment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.dto.PaymentDtos;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Component
public class PaymentMapper {
    private final ObjectMapper objectMapper;
    public PaymentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    public PaymentDtos.PaymentResponse toResponse(Payment payment, List<PaymentAttempt> attempts) {
        List<PaymentDtos.AttemptResponse> attemptResponses = attempts.stream()
                .map(a -> new PaymentDtos.AttemptResponse(a.getAttemptNumber(), a.getProvider(),
                        a.getPaymentMethod() == null ? null : a.getPaymentMethod().name(),
                        a.getStatus().name(), a.getAmount(), a.getCardLast4(), a.getCardNetwork(),
                        a.getFailureCode(), a.getFailureMessage(), a.getCreatedAt()))
                .toList();
        PaymentDtos.QrCode qrCode = (payment.getQrCodeData() == null && payment.getQrCodeImage() == null)
                ? null
                : new PaymentDtos.QrCode(payment.getQrCodeData(), payment.getQrCodeImage());
        return new PaymentDtos.PaymentResponse(
                payment.getPaymentReference(), payment.getMerchantOrderId(), payment.getAmount(),
                payment.getCurrency(), payment.getRefundedAmount(), payment.refundableAmount(),
                payment.getStatus().name(), payment.getEnvironment().name(), payment.getDescription(),
                payment.getProvider(), payment.getCheckoutUrl(), qrCode, payment.getFailureCode(),
                payment.getFailureMessage(), readMetadata(payment.getMetadata()), attemptResponses,
                payment.getCreatedAt(), payment.getUpdatedAt(), payment.getExpiresAt());
    }
    public Map<String, Object> eventPayload(Payment payment) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentReference", payment.getPaymentReference());
        data.put("merchantId", payment.getMerchantId().toString());
        data.put("merchantOrderId", payment.getMerchantOrderId());
        data.put("customerId", payment.getCustomerId() == null ? null : payment.getCustomerId().toString());
        data.put("amount", payment.getAmount());
        data.put("currency", payment.getCurrency());
        data.put("status", payment.getStatus().name());
        data.put("environment", payment.getEnvironment().name());
        data.put("provider", payment.getProvider());
        data.put("providerPaymentId", payment.getProviderPaymentId());
        data.put("refundedAmount", payment.getRefundedAmount());
        data.put("failureCode", payment.getFailureCode());
        data.put("capturedAt", payment.getCapturedAt());
        data.put("createdAt", payment.getCreatedAt());
        return data;
    }
    public String writeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw com.payflow.common.error.PayFlowException.badRequest(
                    com.payflow.common.error.ErrorCode.VALIDATION_FAILED,
                    "metadata could not be serialised");
        }
    }
    @SuppressWarnings("unchecked")
    public Map<String, String> readMetadata(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}

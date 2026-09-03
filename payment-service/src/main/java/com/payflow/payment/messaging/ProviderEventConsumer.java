package com.payflow.payment.messaging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.web.CorrelationId;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
@Component
public class ProviderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(ProviderEventConsumer.class);
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    public ProviderEventConsumer(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }
    @KafkaListener(
            topics = "provider.payment.updated",
            groupId = "payment-service.provider-events",
            containerFactory = "kafkaListenerContainerFactory")
    public void onProviderPaymentUpdated(ConsumerRecord<String, String> record) {
        var correlationHeader = record.headers().lastHeader(CorrelationId.HEADER);
        if (correlationHeader != null) {
            CorrelationId.set(new String(correlationHeader.value(), StandardCharsets.UTF_8));
        }
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            JsonNode data = envelope.path("data");
            paymentService.applyProviderStatus(
                    text(data, "provider"),
                    text(data, "providerPaymentId"),
                    text(data, "status"),
                    text(data, "failureCode"),
                    text(data, "failureMessage"),
                    text(data, "instrumentToken"),
                    text(data, "cardLast4"),
                    text(data, "cardNetwork"),
                    parseMethod(text(data, "paymentMethod")));
        } catch (Exception ex) {
            log.error("Failed to process provider event at offset {}: {}",
                    record.offset(), ex.getMessage(), ex);
            throw new IllegalStateException("Provider event processing failed", ex);
        } finally {
            CorrelationId.clear();
        }
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
    private PaymentAttempt.PaymentMethod parseMethod(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PaymentAttempt.PaymentMethod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

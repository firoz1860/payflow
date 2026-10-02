package com.payflow.provider.service;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.provider.domain.ProviderEvent;
import com.payflow.provider.gateway.PaymentGateway;
import com.payflow.provider.gateway.PaymentGatewayRegistry;
import com.payflow.provider.repository.ProviderEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
@Service
public class ProviderWebhookService {
    private static final Logger log = LoggerFactory.getLogger(ProviderWebhookService.class);
    private static final Duration MAX_TIMESTAMP_SKEW = Duration.ofMinutes(5);
    private static final String TOPIC = "provider.payment.updated";
    private final PaymentGatewayRegistry registry;
    private final ProviderEventRepository eventRepository;
    private final OutboxRecorder outbox;
    private final Counter acceptedCounter;
    private final Counter rejectedCounter;
    private final Counter duplicateCounter;
    public ProviderWebhookService(PaymentGatewayRegistry registry,
                                  ProviderEventRepository eventRepository,
                                  OutboxRecorder outbox, MeterRegistry meterRegistry) {
        this.registry = registry;
        this.eventRepository = eventRepository;
        this.outbox = outbox;
        this.acceptedCounter = Counter.builder("payflow.provider.webhook.accepted")
                .register(meterRegistry);
        this.rejectedCounter = Counter.builder("payflow.provider.webhook.rejected")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("payflow.provider.webhook.duplicate")
                .register(meterRegistry);
    }
    public enum Outcome { ACCEPTED, DUPLICATE, IGNORED }
    @Transactional
    public Outcome handle(String providerName, String rawPayload, String signature, String timestamp) {
        return handle(providerName, rawPayload, signature, timestamp, null);
    }
    @Transactional
    public Outcome handle(String providerName, String rawPayload, String signature, String timestamp, String headerEventId) {
        PaymentGateway gateway = registry.resolve(providerName);
        if (!gateway.verifyWebhook(rawPayload, signature, timestamp)) {
            rejectedCounter.increment();
            log.warn("Rejected {} webhook: signature verification failed", providerName);
            throw PayFlowException.unauthorized("Webhook signature verification failed");
        }
        if (!withinSkew(timestamp)) {
            rejectedCounter.increment();
            log.warn("Rejected {} webhook: timestamp outside the accepted window", providerName);
            throw PayFlowException.unauthorized("Webhook timestamp is outside the accepted window");
        }
        PaymentGateway.GatewayEvent event = gateway.parseEvent(rawPayload);
        com.fasterxml.jackson.databind.JsonNode providerEntity = null;
        if ("razorpay".equals(providerName)) {
            if (!java.util.Set.of("payment.authorized", "payment.captured", "payment.failed", "qr_code.credited").contains(event.eventType())) return Outcome.IGNORED;
            try { providerEntity = new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawPayload).path("payload").path("payment").path("entity"); }
            catch (Exception ex) { throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid provider event"); }
            if (!providerEntity.path("amount").canConvertToLong() || providerEntity.path("amount").asLong() <= 0
                    || !"INR".equals(providerEntity.path("currency").asText()) || providerEntity.path("id").asText().isBlank()
                    || event.providerPaymentId() == null || event.providerPaymentId().isBlank())
                throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Provider event missing payment evidence");
        }
        String eventId = "razorpay".equals(providerName) && headerEventId != null && !headerEventId.isBlank() && headerEventId.length() <= 128
                ? headerEventId : event.providerEventId();
        if (eventId == null || eventId.isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.PROVIDER_ERROR,
                    "Provider event is missing an event id, cannot be deduplicated");
        }
        if (eventRepository.existsByProviderAndProviderEventId(
                event.provider(), eventId)) {
            duplicateCounter.increment();
            log.info("Ignoring duplicate {} event {}", event.provider(), eventId);
            return Outcome.DUPLICATE;
        }
        if (eventRepository.insertIfAbsent(java.util.UUID.randomUUID(), event.provider(), eventId,
                event.eventType(), event.providerPaymentId(), rawPayload) == 0) {
            duplicateCounter.increment();
            return Outcome.DUPLICATE;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", event.provider());
        payload.put("providerEventId", eventId);
        payload.put("providerPaymentId", event.providerPaymentId());
        payload.put("eventType", event.eventType());
        payload.put("status", event.status().name());
        payload.put("instrumentToken", event.instrumentToken());
        payload.put("cardLast4", event.cardLast4());
        payload.put("cardNetwork", event.cardNetwork());
        payload.put("paymentMethod", event.paymentMethod());
        payload.put("failureCode", event.failureCode());
        payload.put("failureMessage", event.failureMessage());
        if (providerEntity != null) {
            payload.put("providerEntityPaymentId", providerEntity.path("id").asText());
            payload.put("amountMinor", providerEntity.path("amount").asLong());
            payload.put("currency", providerEntity.path("currency").asText());
        }
        outbox.record("ProviderEvent", eventId, TOPIC, 1,
                event.providerPaymentId() == null ? event.providerEventId() : event.providerPaymentId(),
                payload);
        acceptedCounter.increment();
        log.info("Accepted {} event {} ({}) for provider payment {}",
                event.provider(), eventId, event.eventType(),
                event.providerPaymentId());
        return Outcome.ACCEPTED;
    }
    private boolean withinSkew(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return true;
        }
        try {
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            return Duration.between(sent, Instant.now()).abs().compareTo(MAX_TIMESTAMP_SKEW) <= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}

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
    public enum Outcome { ACCEPTED, DUPLICATE }
    @Transactional
    public Outcome handle(String providerName, String rawPayload, String signature, String timestamp) {
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
        if (event.providerEventId() == null || event.providerEventId().isBlank()) {
            throw PayFlowException.badRequest(ErrorCode.PROVIDER_ERROR,
                    "Provider event is missing an event id, cannot be deduplicated");
        }
        if (eventRepository.existsByProviderAndProviderEventId(
                event.provider(), event.providerEventId())) {
            duplicateCounter.increment();
            log.info("Ignoring duplicate {} event {}", event.provider(), event.providerEventId());
            return Outcome.DUPLICATE;
        }
        ProviderEvent stored = new ProviderEvent(event.provider(), event.providerEventId(),
                event.eventType(), event.providerPaymentId(), rawPayload);
        try {
            eventRepository.saveAndFlush(stored);
        } catch (DataIntegrityViolationException ex) {
            duplicateCounter.increment();
            log.info("Concurrent duplicate {} event {}", event.provider(), event.providerEventId());
            return Outcome.DUPLICATE;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("provider", event.provider());
        payload.put("providerEventId", event.providerEventId());
        payload.put("providerPaymentId", event.providerPaymentId());
        payload.put("eventType", event.eventType());
        payload.put("status", event.status().name());
        payload.put("instrumentToken", event.instrumentToken());
        payload.put("cardLast4", event.cardLast4());
        payload.put("cardNetwork", event.cardNetwork());
        payload.put("paymentMethod", event.paymentMethod());
        payload.put("failureCode", event.failureCode());
        payload.put("failureMessage", event.failureMessage());
        outbox.record("ProviderEvent", event.providerEventId(), TOPIC, 1,
                event.providerPaymentId() == null ? event.providerEventId() : event.providerPaymentId(),
                payload);
        stored.markProcessed();
        acceptedCounter.increment();
        log.info("Accepted {} event {} ({}) for provider payment {}",
                event.provider(), event.providerEventId(), event.eventType(),
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

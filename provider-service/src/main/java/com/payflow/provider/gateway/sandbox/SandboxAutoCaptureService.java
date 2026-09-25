package com.payflow.provider.gateway.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.service.ProviderWebhookService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SandboxAutoCaptureService {
    private static final Logger log = LoggerFactory.getLogger(SandboxAutoCaptureService.class);
    private static final BigDecimal DECLINE_AT_WEBHOOK = new BigDecimal("0.13");

    private final ProviderProperties.Sandbox config;
    private final ProviderWebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "payflow-sandbox-auto-capture");
                thread.setDaemon(true);
                return thread;
            });

    public SandboxAutoCaptureService(ProviderProperties properties,
                                     ProviderWebhookService webhookService,
                                     ObjectMapper objectMapper) {
        this.config = properties.getSandbox();
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onPaymentCreated(SandboxPaymentCreatedEvent event) {
        if (!config.isAutoCapture()) {
            return;
        }
        long delay = Math.max(500, config.getAutoCaptureDelayMs());
        scheduler.schedule(() -> deliver(event), delay, TimeUnit.MILLISECONDS);
    }

    private void deliver(SandboxPaymentCreatedEvent event) {
        try {
            boolean decline = event.amount() != null
                    && event.amount().remainder(BigDecimal.ONE)
                    .compareTo(DECLINE_AT_WEBHOOK) == 0;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", "evt_sbx_" + UUID.randomUUID().toString().replace("-", ""));
            payload.put("type", decline ? "payment.failed" : "payment.captured");
            payload.put("providerPaymentId", event.providerPaymentId());
            payload.put("status", decline ? "FAILED" : "CAPTURED");
            payload.put("paymentMethod", event.paymentMethod());
            if (decline) {
                payload.put("failureCode", "sandbox_declined");
                payload.put("failureMessage", "The sandbox declined this payment during provider confirmation");
            } else if ("CARD".equalsIgnoreCase(event.paymentMethod())) {
                payload.put("cardLast4", "4242");
                payload.put("cardNetwork", "VISA");
            }

            String body = objectMapper.writeValueAsString(payload);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = Hashing.hmacSha256Hex(
                    config.getWebhookSecret(), timestamp + "." + body);

            webhookService.handle("sandbox", body, signature, timestamp);
            log.info("Sandbox auto-confirmed provider payment {} as {}",
                    event.providerPaymentId(), decline ? "FAILED" : "CAPTURED");
        } catch (Exception ex) {
            log.error("Sandbox auto-confirmation failed for {}: {}",
                    event.providerPaymentId(), ex.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}

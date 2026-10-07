package com.payflow.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * Typed projection of
 * {@code GET {provider-url}/internal/ai/providers/payments/{providerPaymentId}?provider=}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderEvidence(
        String provider,
        String providerPaymentId,
        List<ProviderEvent> events) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderEvent(
            String providerEventId,
            String eventType,
            String processingStatus,
            Instant receivedAt,
            Instant processedAt,
            String failureReason) {
    }
}

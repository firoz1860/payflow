package com.payflow.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Typed projection of {@code GET {merchant-url}/internal/merchants/{merchantId}}.
 * Only non-secret profile fields are mapped — no API-key secrets ever.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MerchantEvidence(
        String id,
        String merchantCode,
        String businessName,
        String status,
        String country,
        String defaultCurrency,
        BigDecimal feePercentage,
        boolean liveModeEnabled,
        Instant createdAt) {
}

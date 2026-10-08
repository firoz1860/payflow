package com.payflow.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Typed projection of {@code GET {ledger-url}/internal/ai/ledger/payments/{paymentReference}}.
 * An absent posting is an empty list (not a 404).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerEvidence(
        String paymentReference,
        List<Posting> postings) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Posting(
            String postingId,
            String sourceType,
            String sourceId,
            String merchantId,
            String currency,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            String description,
            String correlationId,
            Instant createdAt,
            List<Entry> entries) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String entryId,
            String accountId,
            String accountType,
            String entryType,
            BigDecimal amount,
            String currency) {
    }
}

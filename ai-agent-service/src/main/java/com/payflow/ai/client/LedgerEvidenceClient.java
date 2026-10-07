package com.payflow.ai.client;

import com.payflow.ai.client.dto.LedgerEvidence;
import com.payflow.ai.config.AiProperties;
import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads double-entry ledger evidence from ledger-service. The endpoint returns an
 * empty postings list (not 404) when none exist; only transport failure yields
 * empty here.
 */
@Component
public class LedgerEvidenceClient extends InternalEvidenceClientSupport {

    private static final Logger log = LoggerFactory.getLogger(LedgerEvidenceClient.class);

    public LedgerEvidenceClient(ServiceClientFactory factory,
                                @Value("${payflow.services.ledger-url}") String ledgerUrl,
                                AiProperties properties) {
        super(factory, ledgerUrl, properties);
    }

    @CircuitBreaker(name = "ledgerService", fallbackMethod = "fallback")
    public Optional<LedgerEvidence> getLedgerEvidence(String paymentReference) {
        return getOptional(LedgerEvidence.class, "/internal/ai/ledger/payments/{paymentReference}", paymentReference);
    }

    @SuppressWarnings("unused")
    private Optional<LedgerEvidence> fallback(String paymentReference, Throwable t) {
        log.warn("ledger-service evidence unavailable: {}", t.getClass().getSimpleName());
        return Optional.empty();
    }
}

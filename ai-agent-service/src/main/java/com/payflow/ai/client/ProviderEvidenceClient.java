package com.payflow.ai.client;

import com.payflow.ai.client.dto.ProviderEvidence;
import com.payflow.ai.config.AiProperties;
import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads provider webhook/event evidence from provider-service. Absence of events
 * is still a present (empty) {@link ProviderEvidence}; only transport failure or a
 * missing provider payment id yields empty.
 */
@Component
public class ProviderEvidenceClient extends InternalEvidenceClientSupport {

    private static final Logger log = LoggerFactory.getLogger(ProviderEvidenceClient.class);

    public ProviderEvidenceClient(ServiceClientFactory factory,
                                  @Value("${payflow.services.provider-url}") String providerUrl,
                                  AiProperties properties) {
        super(factory, providerUrl, properties);
    }

    @CircuitBreaker(name = "providerService", fallbackMethod = "fallback")
    public Optional<ProviderEvidence> getProviderEvidence(String providerPaymentId, String provider) {
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            return Optional.empty();
        }
        return getOptional(ProviderEvidence.class,
                "/internal/ai/providers/payments/{providerPaymentId}", "provider", provider, providerPaymentId);
    }

    @SuppressWarnings("unused")
    private Optional<ProviderEvidence> fallback(String providerPaymentId, String provider, Throwable t) {
        log.warn("provider-service evidence unavailable: {}", t.getClass().getSimpleName());
        return Optional.empty();
    }
}

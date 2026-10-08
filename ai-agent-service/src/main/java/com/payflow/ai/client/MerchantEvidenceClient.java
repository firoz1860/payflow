package com.payflow.ai.client;

import com.payflow.ai.client.dto.MerchantEvidence;
import com.payflow.ai.config.AiProperties;
import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the caller's own merchant profile from merchant-service (non-secret fields
 * only). 404 or transport failure yields empty.
 */
@Component
public class MerchantEvidenceClient extends InternalEvidenceClientSupport {

    private static final Logger log = LoggerFactory.getLogger(MerchantEvidenceClient.class);

    public MerchantEvidenceClient(ServiceClientFactory factory,
                                  @Value("${payflow.services.merchant-url}") String merchantUrl,
                                  AiProperties properties) {
        super(factory, merchantUrl, properties);
    }

    @CircuitBreaker(name = "merchantService", fallbackMethod = "fallback")
    public Optional<MerchantEvidence> getMerchant(UUID merchantId) {
        if (merchantId == null) {
            return Optional.empty();
        }
        return getOptional(MerchantEvidence.class, "/internal/merchants/{merchantId}", merchantId.toString());
    }

    @SuppressWarnings("unused")
    private Optional<MerchantEvidence> fallback(UUID merchantId, Throwable t) {
        log.warn("merchant-service evidence unavailable: {}", t.getClass().getSimpleName());
        return Optional.empty();
    }
}

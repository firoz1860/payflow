package com.payflow.ai.client;

import com.payflow.ai.client.dto.PaymentEvidence;
import com.payflow.ai.config.AiProperties;
import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Reads authoritative payment evidence from payment-service. Returns
 * {@link Optional#empty()} for an unknown reference (404) and, via the circuit
 * breaker fallback, for any transport failure — the orchestration layer then
 * records "payment not found", never a raw error.
 */
@Component
public class PaymentEvidenceClient extends InternalEvidenceClientSupport {

    private static final Logger log = LoggerFactory.getLogger(PaymentEvidenceClient.class);

    public PaymentEvidenceClient(ServiceClientFactory factory,
                                 @Value("${payflow.services.payment-url}") String paymentUrl,
                                 AiProperties properties) {
        super(factory, paymentUrl, properties);
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallback")
    public Optional<PaymentEvidence> getPayment(String reference) {
        return getOptional(PaymentEvidence.class, "/internal/ai/payments/{reference}", reference);
    }

    @SuppressWarnings("unused")
    private Optional<PaymentEvidence> fallback(String reference, Throwable t) {
        log.warn("payment-service evidence unavailable for a payment reference: {}", t.getClass().getSimpleName());
        return Optional.empty();
    }
}

package com.payflow.payment.client;

import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.ApiKeyVerifier;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

@Component
public class MerchantClient {
    private static final Logger log = LoggerFactory.getLogger(MerchantClient.class);
    private final WebClient client;

    public MerchantClient(ServiceClientFactory factory,
                           @Value("${payflow.services.merchant-url}") String merchantUrl) {
        this.client = factory.create(merchantUrl);
    }

    public record MerchantInfo(
            UUID id,
            String merchantCode,
            String status,
            String defaultCurrency,
            BigDecimal feePercentage,
            BigDecimal fixedFee,
            boolean liveModeEnabled
    ) {
        public boolean canProcessPayments() {
            return "ACTIVE".equalsIgnoreCase(status);
        }

        public String environment() {
            return liveModeEnabled ? "LIVE" : "TEST";
        }

        public ApiKeyVerifier.Verification toVerification() {
            return new ApiKeyVerifier.Verification(
                    true, id, merchantCode, null, environment(), null, java.util.Set.of(),
                    status, canProcessPayments(), defaultCurrency, feePercentage, fixedFee, null);
        }
    }

    @CircuitBreaker(name = "merchantService", fallbackMethod = "fetchFallback")
    public MerchantInfo fetch(UUID merchantId) {
        return client.get()
                .uri("/internal/merchants/{merchantId}", merchantId)
                .retrieve()
                .bodyToMono(MerchantInfo.class)
                .block(Duration.ofSeconds(3));
    }

    @SuppressWarnings("unused")
    private MerchantInfo fetchFallback(UUID merchantId, Throwable throwable) {
        log.error("Merchant service unavailable during merchant lookup for {}: {}",
                merchantId, throwable.toString());
        throw new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Unable to verify merchant account. Please retry.");
    }
}

package com.payflow.payment.client;
import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.security.ApiKeyVerifier;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.Map;
@Component
public class MerchantApiKeyVerifier implements ApiKeyVerifier {
    private static final Logger log = LoggerFactory.getLogger(MerchantApiKeyVerifier.class);
    private final WebClient client;
    public MerchantApiKeyVerifier(ServiceClientFactory factory,
                                  @Value("${payflow.services.merchant-url}") String merchantUrl) {
        this.client = factory.create(merchantUrl);
    }
    @Override
    @CircuitBreaker(name = "merchantService", fallbackMethod = "verifyFallback")
    public Verification verify(String rawApiKey) {
        return client.post()
                .uri("/internal/merchants/api-keys/verify")
                .bodyValue(Map.of("apiKey", rawApiKey))
                .retrieve()
                .bodyToMono(Verification.class)
                .block(Duration.ofSeconds(3));
    }
    @SuppressWarnings("unused")
    private Verification verifyFallback(String rawApiKey, Throwable throwable) {
        log.error("Merchant service unavailable during API key verification: {}", throwable.toString());
        return Verification.invalid("Authentication service is temporarily unavailable");
    }
}

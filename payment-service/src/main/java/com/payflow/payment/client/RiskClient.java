package com.payflow.payment.client;
import com.payflow.common.client.ServiceClientFactory;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
@Component
public class RiskClient {
    private static final Logger log = LoggerFactory.getLogger(RiskClient.class);
    private final WebClient client;
    private final BigDecimal failOpenCeiling;
    public RiskClient(ServiceClientFactory factory,
                      @Value("${payflow.services.risk-url}") String riskUrl,
                      @Value("${payflow.risk.fail-open-ceiling:5000.00}") BigDecimal failOpenCeiling) {
        this.client = factory.create(riskUrl);
        this.failOpenCeiling = failOpenCeiling;
    }
    public enum Decision { ALLOW, REVIEW, BLOCK }
    public record RiskRequest(
            UUID merchantId,
            UUID customerId,
            String paymentReference,
            BigDecimal amount,
            String currency,
            String ipAddress,
            String userAgent,
            String environment
    ) {
    }
    public record RiskResponse(Decision decision, Integer score, List<String> triggeredRules,
                               String reason) {
    }
    @CircuitBreaker(name = "riskService", fallbackMethod = "evaluateFallback")
    public RiskResponse evaluate(RiskRequest request) {
        return client.post()
                .uri("/internal/risk/evaluate")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RiskResponse.class)
                .block(Duration.ofSeconds(2));
    }
    @SuppressWarnings("unused")
    private RiskResponse evaluateFallback(RiskRequest request, Throwable throwable) {
        boolean withinCeiling = request.amount().compareTo(failOpenCeiling) <= 0;
        log.warn("Risk service unavailable for {} ({} {}): failing {}",
                request.paymentReference(), request.amount(), request.currency(),
                withinCeiling ? "open" : "closed");
        return withinCeiling
                ? new RiskResponse(Decision.ALLOW, null, List.of("RISK_SERVICE_UNAVAILABLE"),
                        "Risk service unavailable; amount within fail-open ceiling")
                : new RiskResponse(Decision.REVIEW, null, List.of("RISK_SERVICE_UNAVAILABLE"),
                        "Risk service unavailable; amount above fail-open ceiling");
    }
}

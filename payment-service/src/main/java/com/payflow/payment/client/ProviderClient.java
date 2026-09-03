package com.payflow.payment.client;
import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
@Component
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);
    private final WebClient client;
    public ProviderClient(ServiceClientFactory factory,
                          @Value("${payflow.services.provider-url}") String providerUrl) {
        this.client = factory.create(providerUrl);
    }
    public record CreateProviderPaymentRequest(
            String paymentReference,
            String merchantId,
            BigDecimal amount,
            String currency,
            String description,
            String paymentMethod,
            String returnUrl,
            String environment,
            String idempotencyKey,
            Map<String, String> metadata
    ) {
    }
    public record ProviderPaymentResponse(
            String provider,
            String providerPaymentId,
            String status,
            String checkoutUrl,
            String failureCode,
            String failureMessage,
            String qrCodeData,
            String qrCodeImage
    ) {
    }
    @CircuitBreaker(name = "providerService", fallbackMethod = "createPaymentFallback")
    public ProviderPaymentResponse createPayment(CreateProviderPaymentRequest request) {
        return client.post()
                .uri("/internal/providers/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ProviderPaymentResponse.class)
                .block(Duration.ofSeconds(8));
    }
    @SuppressWarnings("unused")
    private ProviderPaymentResponse createPaymentFallback(CreateProviderPaymentRequest request,
                                                          Throwable throwable) {
        log.error("Provider service call failed for payment {}: {}",
                request.paymentReference(), throwable.toString());
        throw new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE,
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Payment provider is temporarily unavailable. Retry with the same Idempotency-Key.");
    }
    @CircuitBreaker(name = "providerService")
    public ProviderPaymentResponse getPayment(String provider, String providerPaymentId) {
        return client.get()
                .uri("/internal/providers/{provider}/payments/{id}", provider, providerPaymentId)
                .retrieve()
                .bodyToMono(ProviderPaymentResponse.class)
                .block(Duration.ofSeconds(5));
    }
}

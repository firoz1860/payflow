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
import com.payflow.payment.dto.CheckoutDtos;
import org.springframework.web.reactive.function.client.WebClientResponseException;
@Component
public class ProviderClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderClient.class);
    private final WebClient client;
    @Value("${payflow.internal.response-timeout-ms:12000}")
    private long responseTimeoutMs = 12000;
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
    public CheckoutDtos.Options checkout(String orderId) {
        return checkoutResponse(client.get().uri("/internal/providers/razorpay/orders/{id}/checkout", orderId).retrieve(), CheckoutDtos.Options.class);
    }
    public CheckoutDtos.VerifiedPayment verifyCheckout(CheckoutDtos.VerificationRequest evidence) {
        return checkoutResponse(client.post().uri("/internal/providers/razorpay/verify").bodyValue(evidence).retrieve(), CheckoutDtos.VerifiedPayment.class);
    }
    public CheckoutDtos.VerifiedPayment reconcileCheckout(String orderId) {
        return checkoutResponse(client.get().uri("/internal/providers/razorpay/orders/{id}/reconcile", orderId).retrieve(), CheckoutDtos.VerifiedPayment.class);
    }
    private <T> T checkoutResponse(WebClient.ResponseSpec response, Class<T> type) {
        try { return response.bodyToMono(type).block(Duration.ofMillis(responseTimeoutMs + 2000)); }
        catch (WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (java.util.Set.of(400,401,403,404,409,422).contains(status)) {
                org.springframework.http.HttpStatus mapped = status == 401 ? org.springframework.http.HttpStatus.FORBIDDEN : org.springframework.http.HttpStatus.valueOf(status);
                ErrorCode code = ErrorCode.PROVIDER_ERROR;
                throw new PayFlowException(code, mapped, "Provider verification was rejected");
            }
            throw new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Provider verification unavailable; retry verification");
        } catch (RuntimeException ex) {
            throw new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Provider verification unavailable; retry verification");
        }
    }
    @CircuitBreaker(name = "providerService", fallbackMethod = "createPaymentFallback")
    public ProviderPaymentResponse createPayment(CreateProviderPaymentRequest request) {
        return client.post()
                .uri("/internal/providers/payments")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ProviderPaymentResponse.class)
                .block(Duration.ofMillis(responseTimeoutMs + 2000));
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
                .block(Duration.ofMillis(responseTimeoutMs + 2000));
    }
}

package com.payflow.provider.gateway.razorpay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class RazorpayOrderSafetyTest {
    static class MemoryAttempts implements RazorpayOrderAttemptStore {
        private final Map<String, Claim> claims = new java.util.concurrent.ConcurrentHashMap<>();
        public Claim claim(String key, String reference, String hash) {
            Claim old = claims.putIfAbsent(key, new Claim(true, null));
            if (old == null) return new Claim(true, null);
            if (old.responseJson() == null) throw new IllegalStateException("requires reconciliation");
            return new Claim(false, old.responseJson());
        }
        public void complete(String key, String response) { claims.put(key, new Claim(false, response)); }
        public void uncertain(String key) { claims.put(key, new Claim(false, null)); }
    }
    @Test void replayDoesNotCreateAnotherProviderOrder() {
        ProviderProperties p = new ProviderProperties();
        p.getRazorpay().setKeyId("rzp_test_example"); p.getRazorpay().setKeySecret("secret"); p.getRazorpay().setWebhookSecret("webhook");
        RazorpayPaymentGateway gateway = new RazorpayPaymentGateway(p, new ObjectMapper(), new MemoryAttempts());
        AtomicInteger calls = new AtomicInteger();
        WebClient http = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
            .header("Content-Type", "application/json").body("{\"id\":\"order_" + calls.incrementAndGet() + "\"}").build())).build();
        ReflectionTestUtils.setField(gateway, "client", http);
        var command = new PaymentGateway.CreateGatewayPaymentCommand("pay_one", "00000000-0000-0000-0000-000000000001", new BigDecimal("1.00"), "INR", null, "CARD", null, "TEST", "same-key", Map.of());
        assertEquals(gateway.createPayment(command).providerPaymentId(), gateway.createPayment(command).providerPaymentId());
    }
}

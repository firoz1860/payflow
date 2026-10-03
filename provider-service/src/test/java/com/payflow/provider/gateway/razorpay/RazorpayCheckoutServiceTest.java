package com.payflow.provider.gateway.razorpay;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.PayFlowException;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway.GatewayStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
import static com.payflow.provider.gateway.razorpay.RazorpayCheckoutDtos.*;
class RazorpayCheckoutServiceTest {
    private RazorpayCheckoutService service(String json) {
        ProviderProperties p = new ProviderProperties();
        p.getRazorpay().setKeyId("rzp_test_example");p.getRazorpay().setKeySecret("secret");
        var s = new RazorpayCheckoutService(p);
        ReflectionTestUtils.setField(s, "client", WebClient.builder().exchangeFunction(r -> Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(json).build())).build());
        return s;
    }
    @Test void validSignatureFetchesBoundPayment() {
        var s = service("{\"id\":\"pay_real\",\"order_id\":\"order_one\",\"amount\":100,\"currency\":\"INR\",\"status\":\"captured\"}");
        var result = s.verify(new VerificationRequest("order_one", "pay_real", Hashing.hmacSha256Hex("secret", "order_one|pay_real")));
        assertNotNull(result); assertEquals(GatewayStatus.CAPTURED, result.status()); assertEquals(100, result.amountMinor());
    }
    @Test void invalidSignatureRejected() {
        assertThrows(PayFlowException.class, () -> service("{}").verify(new VerificationRequest("order_one", "pay_real", "wrong")));
    }
    @Test void substitutedOrderRejected() {
        assertThrows(PayFlowException.class, () -> service("{\"id\":\"pay_real\",\"order_id\":\"order_other\",\"amount\":100,\"currency\":\"INR\",\"status\":\"captured\"}")
            .verify(new VerificationRequest("order_one", "pay_real", Hashing.hmacSha256Hex("secret", "order_one|pay_real"))));
    }
    @Test void checkoutHasOnlyPublicOptions() {
        var options = service("{\"id\":\"order_one\",\"amount\":100,\"currency\":\"INR\",\"status\":\"created\"}").checkout("order_one");
        assertNotNull(options); assertEquals("rzp_test_example", options.keyId()); assertEquals("TEST",options.mode()); assertEquals(100, options.amountMinor());
    }
}

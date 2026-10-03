package com.payflow.provider.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGatewayRegistry;
import com.payflow.provider.gateway.razorpay.RazorpayPaymentGateway;
import com.payflow.provider.repository.ProviderEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class RazorpayWebhookEvidenceTest {
    private final OutboxRecorder outbox=mock(OutboxRecorder.class);
    private ProviderWebhookService service() {
        var p=new ProviderProperties();p.getRazorpay().setKeyId("rzp_test_example");p.getRazorpay().setKeySecret("secret");p.getRazorpay().setWebhookSecret("webhook");
        var gateway=new RazorpayPaymentGateway(p,new ObjectMapper(),null);
        var repository=mock(ProviderEventRepository.class);
        when(repository.insertIfAbsent(any(),any(),any(),any(),any(),any())).thenReturn(1);
        return new ProviderWebhookService(new PaymentGatewayRegistry(List.of(gateway),"razorpay"),repository,outbox,new SimpleMeterRegistry());
    }
    @Test void unsupportedEventDoesNotBecomePaymentStatus() {
        String body="{\"event\":\"refund.created\",\"created_at\":123,\"payload\":{}}";
        assertEquals("IGNORED",service().handle("razorpay",body,Hashing.hmacSha256Hex("webhook",body),null).name());
    }
    @Test void signedCaptureCarriesAmountAndActualPaymentIdentity() {
        String body="{\"event\":\"payment.captured\",\"created_at\":123,\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_real\",\"order_id\":\"order_one\",\"amount\":100,\"currency\":\"INR\",\"status\":\"captured\"}}}}";
        service().handle("razorpay",body,Hashing.hmacSha256Hex("webhook",body),null);
        ArgumentCaptor<Map<String,Object>> payload=ArgumentCaptor.forClass(Map.class);
        verify(outbox).record(any(),any(),any(),anyInt(),any(),payload.capture());
        assertEquals(100L,payload.getValue().get("amountMinor"));assertEquals("pay_real",payload.getValue().get("providerEntityPaymentId"));assertEquals("INR",payload.getValue().get("currency"));
    }
}

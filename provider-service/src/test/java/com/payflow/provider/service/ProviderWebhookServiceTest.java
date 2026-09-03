package com.payflow.provider.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway;
import com.payflow.provider.gateway.PaymentGatewayRegistry;
import com.payflow.provider.gateway.qr.QrCodeRenderer;
import com.payflow.provider.gateway.sandbox.SandboxPaymentGateway;
import com.payflow.provider.repository.ProviderEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderWebhookServiceTest {
    private static final String SECRET = "sandbox-webhook-secret";
    @Mock
    private ProviderEventRepository eventRepository;
    @Mock
    private OutboxRecorder outbox;
    private ProviderWebhookService service;
    private final String payload = """
            {"eventId":"evt_1","type":"payment.captured","providerPaymentId":"sbx_pay_1",
             "status":"CAPTURED","paymentMethod":"CARD","cardLast4":"4242"}""";
    @BeforeEach
    void setUp() {
        ProviderProperties properties = new ProviderProperties();
        PaymentGateway sandbox = new SandboxPaymentGateway(properties, new ObjectMapper(),
                new QrCodeRenderer());
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(sandbox), "sandbox");
        service = new ProviderWebhookService(registry, eventRepository, outbox,
                new SimpleMeterRegistry());
    }
    private String sign(String body, String timestamp) {
        return Hashing.hmacSha256Hex(SECRET, timestamp + "." + body);
    }
    @Test
    @DisplayName("a correctly signed, fresh event is accepted and published once")
    void validEventAccepted() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        when(eventRepository.existsByProviderAndProviderEventId(any(), any())).thenReturn(false);
        var outcome = service.handle("sandbox", payload, sign(payload, timestamp), timestamp);
        assertThat(outcome).isEqualTo(ProviderWebhookService.Outcome.ACCEPTED);
        verify(outbox).record(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any());
    }
    @Test
    @DisplayName("a forged signature is rejected and nothing is published")
    void forgedSignatureRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        assertThatThrownBy(() -> service.handle("sandbox", payload, "deadbeef", timestamp))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("signature verification failed");
        verify(eventRepository, never()).saveAndFlush(any());
        verify(outbox, never()).record(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any());
    }
    @Test
    @DisplayName("a tampered body invalidates the signature")
    void tamperedBodyRejected() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(payload, timestamp);
        String tampered = payload.replace("sbx_pay_1", "sbx_pay_ATTACKER");
        assertThatThrownBy(() -> service.handle("sandbox", tampered, signature, timestamp))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("signature verification failed");
    }
    @Test
    @DisplayName("a replayed old event is rejected even with a valid signature")
    void staleTimestampRejected() {
        String oldTimestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        assertThatThrownBy(() ->
                service.handle("sandbox", payload, sign(payload, oldTimestamp), oldTimestamp))
                .isInstanceOf(PayFlowException.class)
                .hasMessageContaining("timestamp is outside");
    }
    @Test
    @DisplayName("a duplicate provider event is acknowledged but never processed twice")
    void duplicateEventIgnored() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        when(eventRepository.existsByProviderAndProviderEventId("sandbox", "evt_1")).thenReturn(true);
        var outcome = service.handle("sandbox", payload, sign(payload, timestamp), timestamp);
        assertThat(outcome).isEqualTo(ProviderWebhookService.Outcome.DUPLICATE);
        verify(eventRepository, never()).saveAndFlush(any());
        verify(outbox, never()).record(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any());
    }
}

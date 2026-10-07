package com.payflow.ai.investigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.ai.client.LedgerEvidenceClient;
import com.payflow.ai.client.PaymentEvidenceClient;
import com.payflow.ai.client.ProviderEvidenceClient;
import com.payflow.ai.client.dto.LedgerEvidence;
import com.payflow.ai.client.dto.PaymentEvidence;
import com.payflow.ai.client.dto.ProviderEvidence;
import com.payflow.ai.redaction.Redactor;
import com.payflow.ai.tools.GetLedgerEvidenceTool;
import com.payflow.ai.tools.GetPaymentTool;
import com.payflow.ai.tools.GetProviderEvidenceTool;
import com.payflow.common.security.PayFlowPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the deterministic FACT / INFERENCE / MISSING aggregation and tenancy
 * behaviour with fake (mocked) evidence clients — no network, no real keys.
 */
class PaymentInvestigationServiceTest {

    private final UUID merchantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private PaymentEvidenceClient paymentClient;
    private ProviderEvidenceClient providerClient;
    private LedgerEvidenceClient ledgerClient;
    private PaymentInvestigationService service;

    @BeforeEach
    void setUp() {
        paymentClient = mock(PaymentEvidenceClient.class);
        providerClient = mock(ProviderEvidenceClient.class);
        ledgerClient = mock(LedgerEvidenceClient.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Redactor redactor = new Redactor();
        service = new PaymentInvestigationService(
                new GetPaymentTool(paymentClient, objectMapper, redactor),
                new GetProviderEvidenceTool(providerClient, objectMapper, redactor),
                new GetLedgerEvidenceTool(ledgerClient, objectMapper, redactor));
    }

    private PayFlowPrincipal merchantUser() {
        return PayFlowPrincipal.forUser(userId, merchantId, Set.of("ai:use"));
    }

    private PaymentEvidence payment(String reference, String merchant, String status, String provider,
                                    String providerPaymentId) {
        return new PaymentEvidence(reference, merchant, "order-1", null,
                new BigDecimal("1000.00"), "INR", BigDecimal.ZERO, new BigDecimal("1000.00"),
                status, "LIVE", provider, providerPaymentId, null, null,
                Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, List.of());
    }

    @Test
    void capturedWithProviderProcessedAndBalancedLedgerIsHighConfidence() {
        String ref = "pay_abc123";
        when(paymentClient.getPayment(eq(ref)))
                .thenReturn(Optional.of(payment(ref, merchantId.toString(), "CAPTURED", "razorpay", "rzp_1")));
        when(providerClient.getProviderEvidence(eq("rzp_1"), eq("razorpay")))
                .thenReturn(Optional.of(new ProviderEvidence("razorpay", "rzp_1", List.of(
                        new ProviderEvidence.ProviderEvent("evt_1", "payment.captured", "PROCESSED",
                                Instant.now(), Instant.now(), null)))));
        when(ledgerClient.getLedgerEvidence(eq(ref)))
                .thenReturn(Optional.of(new LedgerEvidence(ref, List.of(
                        new LedgerEvidence.Posting("post_1", "PAYMENT", ref, merchantId.toString(), "INR",
                                new BigDecimal("1000.00"), new BigDecimal("1000.00"), true, "capture", "corr-1",
                                Instant.now(), List.of())))));

        InvestigationResult result = service.investigate(merchantUser(), ref);

        assertThat(result.found()).isTrue();
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.facts()).anyMatch(f -> f.type().equals("PAYMENT_STATUS") && f.value().equals("CAPTURED"));
        assertThat(result.facts()).anyMatch(f -> f.type().equals("PROVIDER_EVENT") && f.value().contains("PROCESSED"));
        assertThat(result.facts()).anyMatch(f -> f.type().equals("LEDGER_POSTING") && f.value().contains("true"));
        assertThat(result.toolResults()).hasSize(3);
    }

    @Test
    void missingLedgerProducesMissingEntryNotFabricatedFact() {
        String ref = "pay_noledger";
        when(paymentClient.getPayment(eq(ref)))
                .thenReturn(Optional.of(payment(ref, merchantId.toString(), "CAPTURED", "razorpay", "rzp_2")));
        when(providerClient.getProviderEvidence(eq("rzp_2"), eq("razorpay")))
                .thenReturn(Optional.of(new ProviderEvidence("razorpay", "rzp_2", List.of(
                        new ProviderEvidence.ProviderEvent("evt_2", "payment.captured", "PROCESSED",
                                Instant.now(), Instant.now(), null)))));
        when(ledgerClient.getLedgerEvidence(eq(ref)))
                .thenReturn(Optional.of(new LedgerEvidence(ref, List.of())));

        InvestigationResult result = service.investigate(merchantUser(), ref);

        assertThat(result.found()).isTrue();
        assertThat(result.missing()).anyMatch(m -> m.toLowerCase().contains("no ledger posting"));
        assertThat(result.facts()).noneMatch(f -> f.type().equals("LEDGER_POSTING"));
        assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
    }

    @Test
    void crossTenantPaymentIsTreatedAsNotFound() {
        String ref = "pay_othertenant";
        String otherMerchant = UUID.randomUUID().toString();
        when(paymentClient.getPayment(eq(ref)))
                .thenReturn(Optional.of(payment(ref, otherMerchant, "CAPTURED", "razorpay", "rzp_3")));

        InvestigationResult result = service.investigate(merchantUser(), ref);

        assertThat(result.found()).isFalse();
        assertThat(result.facts()).isEmpty();
        assertThat(result.confidence()).isEqualTo(Confidence.UNKNOWN);
        assertThat(result.missing()).anyMatch(m -> m.contains("payment not found for this merchant"));
    }

    @Test
    void missingPaymentDoesNotRevealCrossTenantExistence() {
        String ref = "pay_absent";
        when(paymentClient.getPayment(eq(ref))).thenReturn(Optional.empty());

        InvestigationResult result = service.investigate(merchantUser(), ref);

        assertThat(result.found()).isFalse();
        assertThat(result.missing()).containsExactly("payment not found for this merchant");
        // Provider/ledger must not even be consulted once the payment is absent.
        org.mockito.Mockito.verifyNoInteractions(providerClient, ledgerClient);
    }

    @Test
    void adminMayCrossTenant() {
        String ref = "pay_admin";
        String otherMerchant = UUID.randomUUID().toString();
        PayFlowPrincipal admin = PayFlowPrincipal.forUser(userId, merchantId, Set.of("ai:use", "ai:admin"));
        when(paymentClient.getPayment(eq(ref)))
                .thenReturn(Optional.of(payment(ref, otherMerchant, "CAPTURED", "razorpay", "rzp_4")));
        when(providerClient.getProviderEvidence(any(), any())).thenReturn(Optional.empty());
        when(ledgerClient.getLedgerEvidence(eq(ref))).thenReturn(Optional.of(new LedgerEvidence(ref, List.of())));

        InvestigationResult result = service.investigate(admin, ref);

        assertThat(result.found()).isTrue();
        assertThat(result.facts()).anyMatch(f -> f.type().equals("PAYMENT_STATUS"));
    }
}

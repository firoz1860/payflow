package com.payflow.payment.service;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.payment.client.ProviderClient;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.CheckoutDtos;
import com.payflow.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PaymentCheckoutServiceTest {
    private final UUID merchant = UUID.randomUUID();
    private final PayFlowPrincipal principal = PayFlowPrincipal.forUser(UUID.randomUUID(), merchant, Set.of("payments:read","payments:create"));
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ProviderClient provider = mock(ProviderClient.class);
    private final PaymentService state = mock(PaymentService.class);
    private PaymentCheckoutService checkout() { return new PaymentCheckoutService(payments, provider, state); }
    private Payment payment() {
        Payment p = new Payment("pay_test",merchant,null,null,new BigDecimal("1.00"),"INR",Payment.Environment.TEST,null,null,Instant.now().plusSeconds(300));
        p.attachProvider("razorpay", "order_one", null);p.transitionTo(PaymentStatus.PENDING);
        when(payments.findByPaymentReferenceAndMerchantId("pay_test",merchant)).thenReturn(Optional.of(p));return p;
    }
    @Test void crossTenantReturns404() {
        when(payments.findByPaymentReferenceAndMerchantId("pay_other",merchant)).thenReturn(Optional.empty());
        PayFlowException ex = assertThrows(PayFlowException.class, () -> checkout().checkout(principal,"pay_other"));assertEquals(404,ex.getStatus().value());
    }
    @Test void substitutedOrderRejected() {
        payment();
        assertThrows(PayFlowException.class, () -> checkout().verify(principal,"pay_test",new CheckoutDtos.VerificationRequest("order_other","pay_real","a".repeat(64))));
    }
    @Test void capturedCheckoutRejected() {
        payment().transitionTo(PaymentStatus.CAPTURED);
        assertThrows(PayFlowException.class, () -> checkout().checkout(principal,"pay_test"));
    }
}

package com.payflow.payment.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentAttemptRepository;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class VerifiedCaptureTest {
    private final PaymentRepository repository=mock(PaymentRepository.class);
    private final PaymentAttemptRepository attempts=mock(PaymentAttemptRepository.class);
    private final OutboxRecorder outbox=mock(OutboxRecorder.class);
    private final Payment payment=new Payment("pay_test",UUID.randomUUID(),null,null,new BigDecimal("1.00"),"INR",Payment.Environment.TEST,null,null,Instant.now().plusSeconds(300));
    private final PaymentAttempt attempt=new PaymentAttempt(payment.getId(),1,"razorpay",new BigDecimal("1.00"),"INR",PaymentAttempt.PaymentMethod.CARD);
    private PaymentService service() {
        payment.attachProvider("razorpay","order_one",null);payment.transitionTo(PaymentStatus.PENDING);attempt.markPending("order_one");
        when(repository.findByProviderAndProviderPaymentId("razorpay","order_one")).thenReturn(Optional.of(payment));
        when(attempts.findByProviderAndProviderPaymentId("razorpay","order_one")).thenReturn(Optional.of(attempt));
        return new PaymentService(repository,attempts,mock(PaymentWriter.class),new PaymentMapper(new ObjectMapper()),null,null,outbox,new SimpleMeterRegistry());
    }
    private void apply(PaymentService service, String status) { service.applyVerifiedProviderStatus("razorpay","order_one","pay_real",100L,"INR",status,null,null,null,null,null,null); }
    @Test void repeatedCaptureAndDelayedAuthorizationDoNotDowngradeOrEmitTwice() {
        var s=service();apply(s,"CAPTURED");apply(s,"CAPTURED");apply(s,"AUTHORIZED");
        assertEquals(PaymentStatus.CAPTURED,payment.getStatus());assertEquals("pay_real",attempt.getProviderEntityPaymentId());assertEquals("order_one",attempt.getProviderPaymentId());
        verify(outbox,times(1)).record(eq("Payment"),eq("pay_test"),eq("payment.captured"),eq(1),any(),anyMap());
    }
    @Test void failedAttemptDoesNotPreventCapturedRetryOrBindFailedIdentity() {
        var s=service();
        s.applyVerifiedProviderStatus("razorpay","order_one","pay_failed",100L,"INR","FAILED","DECLINED","Declined",null,null,null,null);
        assertEquals(PaymentStatus.PENDING,payment.getStatus());
        assertNull(attempt.getProviderEntityPaymentId());
        apply(s,"AUTHORIZED");apply(s,"CAPTURED");
        s.applyVerifiedProviderStatus("razorpay","order_one","pay_failed",100L,"INR","FAILED",null,null,null,null,null,null);
        assertEquals(PaymentStatus.CAPTURED,payment.getStatus());
        assertEquals("pay_real",attempt.getProviderEntityPaymentId());
        verify(outbox,times(1)).record(eq("Payment"),eq("pay_test"),eq("payment.captured"),eq(1),any(),anyMap());
    }
    @Test void localCancellationCannotHideAProviderCapture() {
        var s=service();
        when(repository.findByPaymentReferenceAndMerchantId("pay_test",payment.getMerchantId())).thenReturn(Optional.of(payment));
        when(repository.findByReferenceForUpdate("pay_test")).thenReturn(Optional.of(payment));
        var principal=com.payflow.common.security.PayFlowPrincipal.forUser(UUID.randomUUID(),payment.getMerchantId(),java.util.Set.of("payments:create"));
        assertThrows(PayFlowException.class,()->s.cancel(principal,"pay_test","Dismissed checkout"));
        assertEquals(PaymentStatus.PENDING,payment.getStatus());
    }
    @Test void mismatchedAmountAndCurrencyCannotCapture() {
        var s=service();assertThrows(PayFlowException.class,()->s.applyVerifiedProviderStatus("razorpay","order_one","pay_real",101L,"INR","CAPTURED",null,null,null,null,null,null));
        assertThrows(PayFlowException.class,()->s.applyVerifiedProviderStatus("razorpay","order_one","pay_real",100L,"USD","CAPTURED",null,null,null,null,null,null));
        assertEquals(PaymentStatus.PENDING,payment.getStatus());assertNull(attempt.getProviderEntityPaymentId());verifyNoInteractions(outbox);
    }
}

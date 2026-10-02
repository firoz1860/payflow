package com.payflow.payment.integration;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.time.Instant;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties="spring.kafka.listener.auto-startup=false")
@Testcontainers @ActiveProfiles("test")
class RazorpayCaptureIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PaymentService service;
    @Autowired JdbcTemplate jdbc;
    @Test void concurrentVerificationAndWebhookEmitOneCapture() throws Exception {
        String reference="pay_"+UUID.randomUUID().toString().replace("-", "");String order="order_"+UUID.randomUUID().toString().replace("-", "");
        Payment p=new Payment(reference,UUID.randomUUID(),null,null,new BigDecimal("1.00"),"INR",Payment.Environment.TEST,null,null,Instant.now().plusSeconds(300));
        p.attachProvider("razorpay",order,null);p.transitionTo(PaymentStatus.PENDING);payments.saveAndFlush(p);
        PaymentAttempt attempt=new PaymentAttempt(p.getId(),1,"razorpay",new BigDecimal("1.00"),"INR",PaymentAttempt.PaymentMethod.CARD);attempt.markPending(order);attempts.saveAndFlush(attempt);
        Runnable capture=()->service.applyVerifiedProviderStatus("razorpay",order,"pay_real",100L,"INR","CAPTURED",null,null,null,null,null,null);
        try(var executor=Executors.newFixedThreadPool(2)) {var a=executor.submit(capture);var b=executor.submit(capture);a.get();b.get();}
        service.applyVerifiedProviderStatus("razorpay",order,"pay_real",100L,"INR","AUTHORIZED",null,null,null,null,null,null);
        assertEquals(PaymentStatus.CAPTURED,payments.findById(p.getId()).orElseThrow().getStatus());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='payment.captured'",Integer.class,reference));
        assertEquals("pay_real",attempts.findById(attempt.getId()).orElseThrow().getProviderEntityPaymentId());
    }
    @Test void failedCallbackThenCapturedReconciliationAndDelayedWebhookConverge() throws Exception {
        String reference="pay_"+UUID.randomUUID().toString().replace("-", "");
        String order="order_"+UUID.randomUUID().toString().replace("-", "");
        Payment p=new Payment(reference,UUID.randomUUID(),null,null,new BigDecimal("1.00"),"INR",Payment.Environment.TEST,null,null,Instant.now().plusSeconds(300));
        p.attachProvider("razorpay",order,null);p.transitionTo(PaymentStatus.PENDING);payments.saveAndFlush(p);
        PaymentAttempt a=new PaymentAttempt(p.getId(),1,"razorpay",new BigDecimal("1.00"),"INR",PaymentAttempt.PaymentMethod.CARD);
        a.markPending(order);attempts.saveAndFlush(a);
        var provider=org.mockito.Mockito.mock(com.payflow.payment.client.ProviderClient.class);
        var checkout=new com.payflow.payment.service.PaymentCheckoutService(payments,provider,service);
        var principal=com.payflow.common.security.PayFlowPrincipal.forUser(UUID.randomUUID(),p.getMerchantId(),java.util.Set.of("payments:read","payments:create"));
        var evidence=new com.payflow.payment.dto.CheckoutDtos.VerificationRequest(order,"pay_failed","a".repeat(64));
        org.mockito.Mockito.when(provider.verifyCheckout(evidence)).thenReturn(new com.payflow.payment.dto.CheckoutDtos.VerifiedPayment(order,"pay_failed",100L,"INR","FAILED"));
        assertEquals("PENDING",checkout.verify(principal,reference,evidence).status());
        service.applyVerifiedProviderStatus("razorpay",order,"pay_success",100L,"INR","AUTHORIZED",null,null,null,null,null,null);
        org.mockito.Mockito.when(provider.reconcileCheckout(order)).thenReturn(new com.payflow.payment.dto.CheckoutDtos.VerifiedPayment(order,"pay_success",100L,"INR","CAPTURED"));
        assertEquals("CAPTURED",checkout.reconcile(principal,reference).status());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var consumer=new com.payflow.payment.messaging.ProviderEventConsumer(service,mapper);
        String delayed=mapper.writeValueAsString(java.util.Map.of("data",java.util.Map.of("provider","razorpay","providerPaymentId",order,"providerEntityPaymentId","pay_failed","amountMinor",100,"currency","INR","status","FAILED")));
        consumer.onProviderPaymentUpdated(new org.apache.kafka.clients.consumer.ConsumerRecord<>("provider.payment.updated",0,0,order,delayed));
        assertEquals(PaymentStatus.CAPTURED,payments.findById(p.getId()).orElseThrow().getStatus());
        assertEquals("pay_success",attempts.findById(a.getId()).orElseThrow().getProviderEntityPaymentId());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='payment.captured'",Integer.class,reference));
    }

}

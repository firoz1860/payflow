package com.payflow.payment.service;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.common.security.ApiKeyVerifier;
import com.payflow.payment.client.ProviderClient;
import com.payflow.payment.client.RiskClient;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.PaymentDtos;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@Component
public class PaymentWriter {
    private static final Logger log = LoggerFactory.getLogger(PaymentWriter.class);
    private static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(30);
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final OutboxRecorder outbox;
    private final ReferenceGenerator references;
    private final PaymentMapper mapper;
    private final Counter failedCounter;
    public PaymentWriter(PaymentRepository paymentRepository,
                         PaymentAttemptRepository attemptRepository,
                         OutboxRecorder outbox, ReferenceGenerator references,
                         PaymentMapper mapper, MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.outbox = outbox;
        this.references = references;
        this.mapper = mapper;
        this.failedCounter = Counter.builder("payflow.payment.failed").register(meterRegistry);
    }
    @Transactional
    public Payment persistNewPayment(UUID merchantId, PaymentDtos.CreatePaymentRequest request,
                                     BigDecimal amount, String currency,
                                     ApiKeyVerifier.Verification merchant,
                                     RiskClient.RiskResponse risk) {
        Payment payment = new Payment(
                references.paymentReference(), merchantId, request.customerId(),
                request.merchantOrderId(), amount, currency,
                Payment.Environment.valueOf(merchant.environment()),
                request.description(), mapper.writeMetadata(request.metadata()),
                Instant.now().plus(DEFAULT_EXPIRY));
        payment.applyRiskDecision(risk.decision().name(), risk.score());
        paymentRepository.save(payment);
        outbox.record("Payment", payment.getPaymentReference(), Topics.PAYMENT_CREATED, 1,
                merchantId.toString(), mapper.eventPayload(payment));
        audit(merchantId, "PAYMENT_CREATED", payment.getPaymentReference());
        return payment;
    }
    @Transactional
    public PaymentDtos.PaymentResponse finaliseCreation(
            UUID paymentId, PaymentAttempt.PaymentMethod method,
            ProviderClient.ProviderPaymentResponse providerResponse) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
        int attemptNumber = attemptRepository.countByPaymentId(paymentId) + 1;
        PaymentAttempt attempt = new PaymentAttempt(paymentId, attemptNumber,
                providerResponse.provider(), payment.getAmount(), payment.getCurrency(), method);
        payment.attachProvider(providerResponse.provider(),
                providerResponse.providerPaymentId(), providerResponse.checkoutUrl());
        if (providerResponse.qrCodeData() != null || providerResponse.qrCodeImage() != null) {
            payment.attachQr(providerResponse.qrCodeData(), providerResponse.qrCodeImage());
        }
        if ("FAILED".equalsIgnoreCase(providerResponse.status())) {
            attempt.markFailed(providerResponse.failureCode(), providerResponse.failureMessage());
            payment.markFailed(providerResponse.failureCode(), providerResponse.failureMessage());
            failedCounter.increment();
            outbox.record("Payment", payment.getPaymentReference(), Topics.PAYMENT_FAILED, 1,
                    payment.getMerchantId().toString(), mapper.eventPayload(payment));
        } else {
            attempt.markPending(providerResponse.providerPaymentId());
            payment.transitionTo(PaymentStatus.PENDING);
            outbox.record("Payment", payment.getPaymentReference(), Topics.PAYMENT_PROCESSING, 1,
                    payment.getMerchantId().toString(), mapper.eventPayload(payment));
        }
        attemptRepository.save(attempt);
        return mapper.toResponse(payment,
                attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId));
    }
    @Transactional
    public void recordProviderFailure(UUID paymentId, PaymentAttempt.PaymentMethod method,
                                      String code, String message) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            int attemptNumber = attemptRepository.countByPaymentId(paymentId) + 1;
            PaymentAttempt attempt = new PaymentAttempt(paymentId, attemptNumber, "unknown",
                    payment.getAmount(), payment.getCurrency(), method);
            attempt.markFailed(code, message);
            attemptRepository.save(attempt);
            log.warn("Recorded failed provider attempt {} for payment {}; payment left in {} "
                            + "for reconciliation", attemptNumber, payment.getPaymentReference(),
                    payment.getStatus());
        });
    }
    void audit(UUID merchantId, String action, String entityId) {
        outbox.record("Payment", entityId, Topics.AUDIT_EVENT, 1, merchantId.toString(),
                Map.of("actorType", "MERCHANT",
                        "actorId", merchantId.toString(),
                        "merchantId", merchantId.toString(),
                        "action", action,
                        "entityType", "Payment",
                        "entityId", entityId));
    }
}

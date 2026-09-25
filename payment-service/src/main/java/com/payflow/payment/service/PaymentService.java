package com.payflow.payment.service;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.money.Money;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.common.security.ApiKeyVerifier;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.common.security.TenantGuard;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentWriter writer;
    private final PaymentMapper mapper;
    private final ProviderClient providerClient;
    private final RiskClient riskClient;
    private final OutboxRecorder outbox;
    private final Counter createdCounter;
    private final Counter riskBlockedCounter;
    private final Counter riskReviewCounter;
    public PaymentService(PaymentRepository paymentRepository,
                          PaymentAttemptRepository attemptRepository,
                          PaymentWriter writer, PaymentMapper mapper,
                          ProviderClient providerClient, RiskClient riskClient,
                          OutboxRecorder outbox, MeterRegistry meterRegistry) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.writer = writer;
        this.mapper = mapper;
        this.providerClient = providerClient;
        this.riskClient = riskClient;
        this.outbox = outbox;
        this.createdCounter = Counter.builder("payflow.payment.created").register(meterRegistry);
        this.riskBlockedCounter = Counter.builder("payflow.payment.risk.blocked").register(meterRegistry);
        this.riskReviewCounter = Counter.builder("payflow.payment.risk.review").register(meterRegistry);
    }
    public PaymentDtos.PaymentResponse create(PayFlowPrincipal principal,
                                              ApiKeyVerifier.Verification merchant,
                                              PaymentDtos.CreatePaymentRequest request,
                                              String idempotencyKey,
                                              String clientIp, String userAgent) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        if (!merchant.canProcessPayments()) {
            throw PayFlowException.unprocessable(ErrorCode.MERCHANT_NOT_ACTIVE,
                    "Merchant status " + merchant.merchantStatus() + " does not permit payments");
        }
        BigDecimal amount = Money.normalize(request.amount(), request.currency());
        Money.requirePositive(amount, "amount");
        String currency = request.currency().toUpperCase();
        if (request.merchantOrderId() != null) {
            paymentRepository.findByMerchantIdAndMerchantOrderId(merchantId, request.merchantOrderId())
                    .ifPresent(existing -> {
                        throw PayFlowException.conflict(ErrorCode.CONFLICT,
                                "A payment already exists for merchantOrderId "
                                        + request.merchantOrderId()
                                        + " (" + existing.getPaymentReference() + ")");
                    });
        }
        RiskClient.RiskResponse risk = riskClient.evaluate(new RiskClient.RiskRequest(
                merchantId, request.customerId(), null, amount, currency,
                clientIp, userAgent, merchant.environment()));
        if (risk.decision() == RiskClient.Decision.BLOCK) {
            riskBlockedCounter.increment();
            log.warn("Risk blocked a {} {} payment for merchant {}: rules={}",
                    amount, currency, merchantId, risk.triggeredRules());
            throw PayFlowException.unprocessable(ErrorCode.RISK_BLOCKED,
                    "This payment was declined by our risk checks");
        }
        Payment payment = writer.persistNewPayment(merchantId, request, amount, currency, merchant, risk);
        createdCounter.increment();
        if (risk.decision() == RiskClient.Decision.REVIEW) {
            riskReviewCounter.increment();
            log.info("Payment {} held for manual review", payment.getPaymentReference());
            return mapper.toResponse(payment, List.of());
        }
        ProviderClient.ProviderPaymentResponse providerResponse;
        try {
            providerResponse = providerClient.createPayment(
                    new ProviderClient.CreateProviderPaymentRequest(
                            payment.getPaymentReference(), merchantId.toString(), amount, currency,
                            request.description(),
                            request.paymentMethod() == null ? null : request.paymentMethod().name(),
                            request.returnUrl(), merchant.environment(),
                            idempotencyKey == null ? payment.getPaymentReference() : idempotencyKey,
                            request.metadata()));
        } catch (RuntimeException ex) {
            writer.recordProviderFailure(payment.getId(), request.paymentMethod(),
                    "PROVIDER_UNAVAILABLE", ex.getMessage());
            throw ex;
        }
        return writer.finaliseCreation(payment.getId(), request.paymentMethod(), providerResponse);
    }
    @Transactional
    public void applyProviderStatus(String provider, String providerPaymentId,
                                    String providerStatus, String failureCode,
                                    String failureMessage, String instrumentToken,
                                    String cardLast4, String cardNetwork,
                                    PaymentAttempt.PaymentMethod method) {
        Payment payment = paymentRepository
                .findByProviderAndProviderPaymentId(provider, providerPaymentId)
                .orElse(null);
        if (payment == null) {
            log.warn("Provider event for unknown {} payment {}", provider, providerPaymentId);
            return;
        }
        PaymentStatus target = mapProviderStatus(providerStatus);
        if (payment.getStatus() == target) {
            log.debug("Duplicate provider event for {}, ignoring", payment.getPaymentReference());
            return;
        }
        if (!payment.getStatus().canTransitionTo(target)) {
            log.warn("Ignoring out-of-order provider event for {}: {} -> {}",
                    payment.getPaymentReference(), payment.getStatus(), target);
            return;
        }
        attemptRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId)
                .ifPresent(attempt -> {
                    attempt.attachInstrument(instrumentToken, cardLast4, cardNetwork, method);
                    if (target == PaymentStatus.CAPTURED || target == PaymentStatus.AUTHORIZED) {
                        attempt.markSucceeded(providerPaymentId);
                    } else if (target == PaymentStatus.FAILED) {
                        attempt.markFailed(failureCode, failureMessage);
                    }
                });
        if (target == PaymentStatus.FAILED) {
            payment.markFailed(failureCode, failureMessage);
            outbox.record("Payment", payment.getPaymentReference(), Topics.PAYMENT_FAILED, 1,
                    payment.getMerchantId().toString(), mapper.eventPayload(payment));
        } else {
            payment.transitionTo(target);
            String topic = switch (target) {
                case AUTHORIZED -> Topics.PAYMENT_AUTHORIZED;
                case CAPTURED -> Topics.PAYMENT_CAPTURED;
                // CANCELLED/EXPIRED are terminal: notify on the failure topic (as the
                // merchant-initiated cancel path does), not the non-terminal processing topic.
                case CANCELLED -> Topics.PAYMENT_FAILED;
                default -> Topics.PAYMENT_PROCESSING;
            };
            outbox.record("Payment", payment.getPaymentReference(), topic, 1,
                    payment.getMerchantId().toString(), mapper.eventPayload(payment));
        }
        writer.audit(payment.getMerchantId(), "PAYMENT_" + target.name(), payment.getPaymentReference());
        log.info("Payment {} -> {} from {} event", payment.getPaymentReference(), target, provider);
    }
    private PaymentStatus mapProviderStatus(String providerStatus) {
        return switch (providerStatus == null ? "" : providerStatus.toUpperCase()) {
            case "AUTHORIZED", "REQUIRES_CAPTURE" -> PaymentStatus.AUTHORIZED;
            case "CAPTURED", "SUCCEEDED", "PAID" -> PaymentStatus.CAPTURED;
            case "FAILED", "DECLINED" -> PaymentStatus.FAILED;
            case "CANCELLED", "CANCELED", "EXPIRED" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PROCESSING;
        };
    }
    @Transactional
    public BigDecimal registerRefund(String paymentReference, BigDecimal refundAmount,
                                     String refundReference) {
        Payment payment = paymentRepository.findByReferenceForUpdate(paymentReference)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
        BigDecimal normalised = Money.normalize(refundAmount, payment.getCurrency());
        Money.requirePositive(normalised, "amount");
        payment.registerRefund(normalised);
        outbox.record("Payment", payment.getPaymentReference(), Topics.REFUND_COMPLETED, 1,
                payment.getMerchantId().toString(),
                Map.of("paymentReference", payment.getPaymentReference(),
                        "merchantId", payment.getMerchantId().toString(),
                        "refundReference", refundReference,
                        "refundAmount", normalised,
                        "refundedAmountTotal", payment.getRefundedAmount(),
                        "currency", payment.getCurrency(),
                        "status", payment.getStatus().name()));
        log.info("Refund {} of {} registered against {}; refundable balance now {}",
                refundReference, normalised, paymentReference, payment.refundableAmount());
        return payment.refundableAmount();
    }
    @Transactional(readOnly = true)
    public PaymentDtos.PaymentResponse get(PayFlowPrincipal principal, String paymentReference) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        Payment payment = paymentRepository
                .findByPaymentReferenceAndMerchantId(paymentReference, merchantId)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
        return mapper.toResponse(payment,
                attemptRepository.findByPaymentIdOrderByAttemptNumberAsc(payment.getId()));
    }
    @Transactional(readOnly = true)
    public PaymentDtos.PageResponse<PaymentDtos.PaymentResponse> list(PayFlowPrincipal principal,
                                                                      PaymentStatus status,
                                                                      Pageable pageable) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        Page<Payment> page = status == null
                ? paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                : paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtDesc(
                        merchantId, status, pageable);
        List<UUID> paymentIds = page.getContent().stream().map(Payment::getId).toList();
        Map<UUID, List<PaymentAttempt>> attemptsByPayment = paymentIds.isEmpty()
                ? Map.of()
                : attemptRepository
                        .findByPaymentIdInOrderByPaymentIdAscAttemptNumberAsc(paymentIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(PaymentAttempt::getPaymentId));
        List<PaymentDtos.PaymentResponse> data = page.getContent().stream()
                .map(p -> mapper.toResponse(
                        p, attemptsByPayment.getOrDefault(p.getId(), List.of())))
                .toList();
        return new PaymentDtos.PageResponse<>(data, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
    @Transactional
    public PaymentDtos.PaymentResponse cancel(PayFlowPrincipal principal, String paymentReference,
                                              String reason) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        Payment payment = paymentRepository
                .findByPaymentReferenceAndMerchantId(paymentReference, merchantId)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
        payment.transitionTo(PaymentStatus.CANCELLED);
        outbox.record("Payment", payment.getPaymentReference(), Topics.PAYMENT_FAILED, 1,
                merchantId.toString(), mapper.eventPayload(payment));
        writer.audit(merchantId, "PAYMENT_CANCELLED", paymentReference);
        log.info("Payment {} cancelled by merchant {} ({})", paymentReference, merchantId, reason);
        return mapper.toResponse(payment, List.of());
    }
    @Transactional(readOnly = true)
    public Payment requireOwnedPayment(UUID merchantId, String paymentReference) {
        return paymentRepository.findByPaymentReferenceAndMerchantId(paymentReference, merchantId)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
    }
    @Transactional(readOnly = true)
    public Payment findByReference(String paymentReference) {
        return paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> PayFlowException.notFound("Payment not found"));
    }
}

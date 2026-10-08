package com.payflow.payment.controller;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.domain.PaymentAttempt;
import com.payflow.payment.dto.InternalAiPaymentDtos;
import com.payflow.payment.repository.PaymentAttemptRepository;
import com.payflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController
@RequestMapping("/internal/ai/payments")
@Hidden
public class InternalAiPaymentController {
    private final PaymentService paymentService;
    private final PaymentAttemptRepository attemptRepository;
    public InternalAiPaymentController(PaymentService paymentService,
                                       PaymentAttemptRepository attemptRepository) {
        this.paymentService = paymentService;
        this.attemptRepository = attemptRepository;
    }
    @GetMapping("/{reference}")
    public InternalAiPaymentDtos.PaymentEvidence get(@PathVariable String reference) {
        Payment payment = paymentService.findByReference(reference);
        List<InternalAiPaymentDtos.AttemptEvidence> attempts = attemptRepository
                .findByPaymentIdOrderByAttemptNumberAsc(payment.getId()).stream()
                .map(this::toAttemptEvidence)
                .toList();
        return new InternalAiPaymentDtos.PaymentEvidence(
                payment.getPaymentReference(),
                payment.getMerchantId().toString(),
                payment.getMerchantOrderId(),
                payment.getCustomerId() == null ? null : payment.getCustomerId().toString(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getRefundedAmount(),
                payment.refundableAmount(),
                payment.getStatus().name(),
                payment.getEnvironment().name(),
                payment.getProvider(),
                payment.getProviderPaymentId(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getAuthorizedAt(),
                payment.getCapturedAt(),
                payment.getExpiresAt(),
                attempts);
    }
    private InternalAiPaymentDtos.AttemptEvidence toAttemptEvidence(PaymentAttempt attempt) {
        return new InternalAiPaymentDtos.AttemptEvidence(
                attempt.getAttemptNumber(),
                attempt.getProvider(),
                attempt.getProviderPaymentId(),
                attempt.getPaymentMethod() == null ? null : attempt.getPaymentMethod().name(),
                attempt.getStatus().name(),
                attempt.getAmount(),
                attempt.getCurrency(),
                attempt.getCardLast4(),
                attempt.getCardNetwork(),
                attempt.getFailureCode(),
                attempt.getFailureMessage(),
                attempt.getCreatedAt());
    }
}

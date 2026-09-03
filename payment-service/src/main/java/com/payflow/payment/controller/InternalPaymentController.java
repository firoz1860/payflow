package com.payflow.payment.controller;
import com.payflow.payment.dto.PaymentDtos;
import com.payflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.Map;
@RestController
@RequestMapping("/internal/payments")
@Hidden
public class InternalPaymentController {
    private final PaymentService paymentService;
    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
    @GetMapping("/{paymentReference}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String paymentReference) {
        var payment = paymentService.findByReference(paymentReference);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("paymentReference", payment.getPaymentReference());
        body.put("merchantId", payment.getMerchantId().toString());
        body.put("amount", payment.getAmount());
        body.put("refundedAmount", payment.getRefundedAmount());
        body.put("refundableAmount", payment.refundableAmount());
        body.put("currency", payment.getCurrency());
        body.put("status", payment.getStatus().name());
        body.put("provider", payment.getProvider());
        body.put("providerPaymentId", payment.getProviderPaymentId());
        body.put("refundable", payment.getStatus().isRefundable());
        return ResponseEntity.ok(body);
    }
    @PostMapping("/refunds/register")
    public ResponseEntity<Map<String, BigDecimal>> registerRefund(
            @Valid @RequestBody PaymentDtos.RegisterRefundRequest request) {
        BigDecimal remaining = paymentService.registerRefund(
                request.paymentReference(), request.amount(), request.refundReference());
        return ResponseEntity.ok(Map.of("refundableAmount", remaining));
    }
}

package com.payflow.provider.controller;
import com.payflow.provider.gateway.PaymentGateway;
import com.payflow.provider.gateway.PaymentGatewayRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/internal/providers")
@Hidden
public class InternalProviderController {
    private final PaymentGatewayRegistry registry;
    public InternalProviderController(PaymentGatewayRegistry registry) {
        this.registry = registry;
    }
    public record CreatePaymentRequest(
            @NotNull String paymentReference,
            @NotNull String merchantId,
            @NotNull BigDecimal amount,
            @NotNull String currency,
            String description,
            String paymentMethod,
            String returnUrl,
            @NotNull String environment,
            String idempotencyKey,
            Map<String, String> metadata
    ) {
    }
    public record ProviderPaymentResponse(
            String provider, String providerPaymentId, String status,
            String checkoutUrl, String failureCode, String failureMessage,
            String qrCodeData, String qrCodeImage
    ) {
    }
    public record CreateRefundRequest(
            @NotNull String provider,
            @NotNull String providerPaymentId,
            @NotNull String refundReference,
            @NotNull BigDecimal amount,
            @NotNull String currency,
            String reason,
            String idempotencyKey
    ) {
    }
    @PostMapping("/payments")
    public ResponseEntity<ProviderPaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentGateway gateway = registry.selectFor(
                request.environment(), request.currency(), request.paymentMethod());
        PaymentGateway.GatewayPayment result = gateway.createPayment(
                new PaymentGateway.CreateGatewayPaymentCommand(
                        request.paymentReference(), request.merchantId(), request.amount(),
                        request.currency(), request.description(), request.paymentMethod(),
                        request.returnUrl(), request.environment(),
                        request.idempotencyKey(), request.metadata()));
        return ResponseEntity.ok(new ProviderPaymentResponse(
                result.provider(), result.providerPaymentId(), result.status().name(),
                result.checkoutUrl(), result.failureCode(), result.failureMessage(),
                result.qrCodeData(), result.qrCodeImage()));
    }
    @GetMapping("/{provider}/payments/{providerPaymentId}")
    public ResponseEntity<ProviderPaymentResponse> getPayment(@PathVariable String provider,
                                                              @PathVariable String providerPaymentId) {
        PaymentGateway.GatewayPayment result = registry.resolve(provider).getPayment(providerPaymentId);
        return ResponseEntity.ok(new ProviderPaymentResponse(
                result.provider(), result.providerPaymentId(), result.status().name(),
                result.checkoutUrl(), result.failureCode(), result.failureMessage(),
                result.qrCodeData(), result.qrCodeImage()));
    }
    @PostMapping("/refunds")
    public ResponseEntity<PaymentGateway.GatewayRefund> createRefund(
            @Valid @RequestBody CreateRefundRequest request) {
        PaymentGateway.GatewayRefund refund = registry.resolve(request.provider())
                .createRefund(new PaymentGateway.RefundCommand(
                        request.providerPaymentId(), request.refundReference(), request.amount(),
                        request.currency(), request.reason(), request.idempotencyKey()));
        return ResponseEntity.ok(refund);
    }
}

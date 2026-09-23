package com.payflow.payment.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.ApiKeyVerifier;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.payment.client.MerchantClient;
import com.payflow.payment.domain.PaymentStatus;
import com.payflow.payment.dto.PaymentDtos;
import com.payflow.payment.service.IdempotencyService;
import com.payflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MerchantClient merchantClient;
    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService,
                             ObjectMapper objectMapper, MerchantClient merchantClient) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.merchantClient = merchantClient;
    }
    @PostMapping
    @PreAuthorize("hasAuthority('payments:create')")
    @Operation(summary = "Create a payment (idempotent)")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @Parameter(description = "Unique per logical operation; safe to retry with the same value")
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentDtos.CreatePaymentRequest request,
            HttpServletRequest http) {
        ApiKeyVerifier.Verification merchant = resolveMerchantContext(principal, http);
        String canonicalBody = serialise(request);
        IdempotencyService.Claim claim = idempotencyService.claim(
                principal.merchantId(), idempotencyKey, "POST /api/v1/payments", canonicalBody);
        if (claim instanceof IdempotencyService.Claim.Replay replay) {
            return ResponseEntity.status(replay.responseCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotent-Replay", "true")
                    .body(replay.responseBody());
        }
        java.util.UUID recordId = ((IdempotencyService.Claim.Acquired) claim).recordId();
        try {
            PaymentDtos.PaymentResponse response = paymentService.create(
                    principal, merchant, request, idempotencyKey,
                    clientIp(http), http.getHeader("User-Agent"));
            idempotencyService.complete(recordId, response.paymentReference(),
                    HttpStatus.CREATED.value(), serialise(response));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException ex) {
            idempotencyService.fail(recordId);
            throw ex;
        }
    }
    @GetMapping("/{paymentReference}")
    @PreAuthorize("hasAuthority('payments:read')")
    public ResponseEntity<PaymentDtos.PaymentResponse> get(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable String paymentReference) {
        return ResponseEntity.ok(paymentService.get(principal, paymentReference));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('payments:read')")
    public ResponseEntity<PaymentDtos.PageResponse<PaymentDtos.PaymentResponse>> list(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(paymentService.list(principal, status,
                PageRequest.of(Math.max(page, 0), cappedSize)));
    }
    @PostMapping("/{paymentReference}/cancel")
    @PreAuthorize("hasAuthority('payments:create')")
    public ResponseEntity<PaymentDtos.PaymentResponse> cancel(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable String paymentReference,
            @Valid @RequestBody(required = false) PaymentDtos.CancelPaymentRequest request) {
        return ResponseEntity.ok(paymentService.cancel(principal, paymentReference,
                request == null ? null : request.reason()));
    }
    private ApiKeyVerifier.Verification resolveMerchantContext(PayFlowPrincipal principal,
                                                                HttpServletRequest http) {
        Object attribute = http.getAttribute(ApiKeyVerifier.REQUEST_ATTRIBUTE);
        if (attribute instanceof ApiKeyVerifier.Verification verification) {
            return verification;
        }
        if (principal == null || principal.merchantId() == null) {
            throw PayFlowException.forbidden("This operation requires a merchant context");
        }
        MerchantClient.MerchantInfo info = merchantClient.fetch(principal.merchantId());
        return info.toVerification();
    }
    private String serialise(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialise payload", ex);
        }
    }
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

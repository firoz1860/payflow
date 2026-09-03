package com.payflow.merchant.controller;
import com.payflow.common.error.PayFlowException;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.service.ApiKeyService;
import com.payflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController
@RequestMapping("/internal/merchants")
@Hidden
public class InternalMerchantController {
    private final ApiKeyService apiKeyService;
    private final MerchantService merchantService;
    public InternalMerchantController(ApiKeyService apiKeyService, MerchantService merchantService) {
        this.apiKeyService = apiKeyService;
        this.merchantService = merchantService;
    }
    @PostMapping("/api-keys/verify")
    public ResponseEntity<MerchantDtos.ApiKeyVerificationResponse> verify(
            @Valid @RequestBody MerchantDtos.VerifyApiKeyRequest request) {
        return ResponseEntity.ok(apiKeyService.verify(request.apiKey()));
    }
    @GetMapping("/{merchantId}")
    public ResponseEntity<MerchantDtos.MerchantResponse> get(@PathVariable UUID merchantId) {
        try {
            return ResponseEntity.ok(merchantService.get(merchantId));
        } catch (PayFlowException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}

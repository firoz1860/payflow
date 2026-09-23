package com.payflow.merchant.controller;
import com.payflow.common.security.PayFlowPrincipal;
import com.payflow.common.security.TenantGuard;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.service.ApiKeyService;
import com.payflow.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchants")
public class MerchantController {
    private final MerchantService merchantService;
    private final ApiKeyService apiKeyService;
    public MerchantController(MerchantService merchantService, ApiKeyService apiKeyService) {
        this.merchantService = merchantService;
        this.apiKeyService = apiKeyService;
    }
    @PostMapping
    @PreAuthorize("hasAuthority('platform:admin')")
    @Operation(summary = "Onboard a merchant (platform admin only)")
    public ResponseEntity<MerchantDtos.MerchantResponse> create(
            @Valid @RequestBody MerchantDtos.CreateMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(merchantService.create(request));
    }
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('merchant:read')")
    @Operation(summary = "Fetch the caller's own merchant")
    public ResponseEntity<MerchantDtos.MerchantResponse> me(
            @AuthenticationPrincipal PayFlowPrincipal principal) {
        return ResponseEntity.ok(merchantService.get(TenantGuard.requireMerchant(principal)));
    }
    @PatchMapping("/me")
    @PreAuthorize("hasAuthority('merchant:manage')")
    public ResponseEntity<MerchantDtos.MerchantResponse> updateMe(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @Valid @RequestBody MerchantDtos.UpdateMerchantRequest request) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        return ResponseEntity.ok(merchantService.update(merchantId, principal.userId(), request));
    }
    @GetMapping
    @PreAuthorize("hasAuthority('platform:admin')")
    @Operation(summary = "List all merchants (paginated, platform admin only)")
    public ResponseEntity<org.springframework.data.domain.Page<MerchantDtos.MerchantResponse>> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(merchantService.list(
                org.springframework.data.domain.PageRequest.of(Math.max(page, 0), cappedSize)));
    }
    @GetMapping("/{merchantId}")
    @PreAuthorize("hasAuthority('platform:admin')")
    public ResponseEntity<MerchantDtos.MerchantResponse> getById(@PathVariable UUID merchantId) {
        return ResponseEntity.ok(merchantService.get(merchantId));
    }
    @PatchMapping("/{merchantId}/status")
    @PreAuthorize("hasAuthority('platform:admin')")
    @Operation(summary = "Suspend, block or reactivate a merchant; revokes API keys on suspension")
    public ResponseEntity<MerchantDtos.MerchantResponse> changeStatus(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantDtos.ChangeStatusRequest request) {
        return ResponseEntity.ok(merchantService.changeStatus(merchantId, principal.userId(), request));
    }
    @PatchMapping("/{merchantId}/pricing")
    @PreAuthorize("hasAuthority('platform:admin')")
    public ResponseEntity<MerchantDtos.MerchantResponse> updatePricing(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable UUID merchantId,
            @Valid @RequestBody MerchantDtos.UpdatePricingRequest request) {
        return ResponseEntity.ok(merchantService.updatePricing(merchantId, principal.userId(), request));
    }
    @PostMapping("/{merchantId}/live-mode")
    @PreAuthorize("hasAuthority('platform:admin')")
    public ResponseEntity<MerchantDtos.MerchantResponse> enableLiveMode(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable UUID merchantId) {
        return ResponseEntity.ok(merchantService.enableLiveMode(merchantId, principal.userId()));
    }
    @PostMapping("/me/api-keys")
    @PreAuthorize("hasAuthority('api_keys:manage')")
    @Operation(summary = "Issue an API key. The secret is returned once and never again.")
    public ResponseEntity<MerchantDtos.CreateApiKeyResponse> createApiKey(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @Valid @RequestBody MerchantDtos.CreateApiKeyRequest request) {
        UUID merchantId = TenantGuard.requireMerchant(principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.create(merchantId, principal.userId(), request));
    }
    @GetMapping("/me/api-keys")
    @PreAuthorize("hasAuthority('api_keys:manage')")
    public ResponseEntity<List<MerchantDtos.ApiKeyResponse>> listApiKeys(
            @AuthenticationPrincipal PayFlowPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.list(TenantGuard.requireMerchant(principal)));
    }
    @DeleteMapping("/me/api-keys/{keyId}")
    @PreAuthorize("hasAuthority('api_keys:manage')")
    public ResponseEntity<MerchantDtos.MessageResponse> revokeApiKey(
            @AuthenticationPrincipal PayFlowPrincipal principal,
            @PathVariable String keyId) {
        apiKeyService.revoke(TenantGuard.requireMerchant(principal), keyId, principal.userId());
        return ResponseEntity.ok(new MerchantDtos.MessageResponse("API key revoked"));
    }
}

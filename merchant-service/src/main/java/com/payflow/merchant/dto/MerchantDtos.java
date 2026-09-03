package com.payflow.merchant.dto;
import com.payflow.merchant.domain.ApiKey;
import com.payflow.merchant.domain.Merchant;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public final class MerchantDtos {
    private MerchantDtos() {
    }
    public record CreateMerchantRequest(
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 32) @Pattern(regexp = "^[+0-9 ()-]*$") String phone,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code")
            String country,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO 4217 code")
            String defaultCurrency
    ) {
    }
    public record UpdateMerchantRequest(
            @Size(max = 200) String businessName,
            @Size(max = 32) @Pattern(regexp = "^[+0-9 ()-]*$") String phone,
            @Pattern(regexp = "^[A-Za-z]{3}$") String defaultCurrency
    ) {
    }
    public record UpdatePricingRequest(
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal feePercentage,
            @DecimalMin("0.0000") BigDecimal fixedFee,
            Integer settlementDelayDays
    ) {
    }
    public record ChangeStatusRequest(
            @NotNull Merchant.Status status,
            @Size(max = 500) String reason
    ) {
    }
    public record MerchantResponse(
            UUID id,
            String merchantCode,
            String businessName,
            String email,
            String phone,
            String status,
            String country,
            String defaultCurrency,
            BigDecimal feePercentage,
            BigDecimal fixedFee,
            int settlementDelayDays,
            boolean liveModeEnabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
    public record CreateApiKeyRequest(
            @NotNull ApiKey.Environment environment,
            @NotNull ApiKey.KeyType keyType,
            @Size(max = 120) String label,
            Set<String> scopes,
            Instant expiresAt
    ) {
    }
    public record CreateApiKeyResponse(
            String keyId,
            String secret,
            String maskedKey,
            String environment,
            String keyType,
            Set<String> scopes,
            Instant expiresAt,
            String warning
    ) {
    }
    public record ApiKeyResponse(
            String keyId,
            String maskedKey,
            String environment,
            String keyType,
            String status,
            String label,
            Set<String> scopes,
            Instant createdAt,
            Instant expiresAt,
            Instant lastUsedAt
    ) {
    }
    public record ApiKeyVerificationResponse(
            boolean valid,
            UUID merchantId,
            String merchantCode,
            String keyId,
            String environment,
            String keyType,
            Set<String> scopes,
            String merchantStatus,
            boolean canProcessPayments,
            String defaultCurrency,
            BigDecimal feePercentage,
            BigDecimal fixedFee,
            String reason
    ) {
        public static ApiKeyVerificationResponse invalid(String reason) {
            return new ApiKeyVerificationResponse(false, null, null, null, null, null,
                    Set.of(), null, false, null, null, null, reason);
        }
    }
    public record VerifyApiKeyRequest(@NotBlank String apiKey) {
    }
    public record MessageResponse(String message) {
    }
}

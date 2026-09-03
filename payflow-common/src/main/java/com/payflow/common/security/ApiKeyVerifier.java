package com.payflow.common.security;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
public interface ApiKeyVerifier {
    String REQUEST_ATTRIBUTE = "payflow.apiKeyVerification";
    Verification verify(String rawApiKey);
    record Verification(
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
        public static Verification invalid(String reason) {
            return new Verification(false, null, null, null, null, null, Set.of(),
                    null, false, null, null, null, reason);
        }
    }
}

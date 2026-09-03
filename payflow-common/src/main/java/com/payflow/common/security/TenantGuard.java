package com.payflow.common.security;
import com.payflow.common.error.PayFlowException;
import java.util.UUID;
public final class TenantGuard {
    private TenantGuard() {
    }
    public static UUID requireMerchant(PayFlowPrincipal principal) {
        if (principal == null || principal.merchantId() == null) {
            throw PayFlowException.forbidden("This operation requires a merchant context");
        }
        return principal.merchantId();
    }
    public static void assertOwnership(PayFlowPrincipal principal, UUID resourceMerchantId,
                                       String resourceName) {
        UUID callerMerchant = requireMerchant(principal);
        if (!callerMerchant.equals(resourceMerchantId)) {
            throw PayFlowException.notFound(resourceName + " not found");
        }
    }
    public static void requirePermission(PayFlowPrincipal principal, String permission) {
        if (principal == null || !principal.hasPermission(permission)) {
            throw PayFlowException.forbidden("Missing required permission: " + permission);
        }
    }
}

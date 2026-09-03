package com.payflow.common.security;
import java.util.Set;
import java.util.UUID;
public record PayFlowPrincipal(
        AuthType authType,
        UUID userId,
        UUID merchantId,
        String keyId,
        Environment environment,
        Set<String> permissions
) {
    public enum AuthType { USER, API_KEY }
    public enum Environment { TEST, LIVE }
    public boolean isLive() {
        return environment == Environment.LIVE;
    }
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
    public static PayFlowPrincipal forApiKey(UUID merchantId, String keyId,
                                             Environment environment, Set<String> permissions) {
        return new PayFlowPrincipal(AuthType.API_KEY, null, merchantId, keyId, environment, permissions);
    }
    public static PayFlowPrincipal forUser(UUID userId, UUID merchantId, Set<String> permissions) {
        return new PayFlowPrincipal(AuthType.USER, userId, merchantId, null, null, permissions);
    }
}

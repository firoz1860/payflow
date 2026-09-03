package com.payflow.auth.security;
import java.util.Set;
import java.util.UUID;
public record AuthenticatedUser(UUID userId, String email, UUID merchantId,
                                Set<String> roles, Set<String> permissions) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}

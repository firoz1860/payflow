package com.payflow.common.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
public class JwtTokenValidator {
    private final SecretKey key;
    private final String issuer;
    public JwtTokenValidator(String secret, String issuer) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) {
            throw new IllegalStateException("JWT secret must be at least 64 bytes for HS512");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = issuer;
    }
    public PayFlowPrincipal validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String merchantId = claims.get("merchantId", String.class);
        return PayFlowPrincipal.forUser(
                UUID.fromString(claims.getSubject()),
                merchantId == null || merchantId.isBlank() ? null : UUID.fromString(merchantId),
                stringSet(claims.get("permissions")));
    }
    private Set<String> stringSet(Object claim) {
        if (claim instanceof List<?> list) {
            return list.stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return Set.of();
    }
}

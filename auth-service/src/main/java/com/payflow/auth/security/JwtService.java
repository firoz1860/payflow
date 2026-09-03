package com.payflow.auth.security;
import com.payflow.auth.config.JwtProperties;
import com.payflow.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;
    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 64) {
            throw new IllegalStateException(
                    "payflow.jwt.secret must be at least 64 bytes for HS512. "
                            + "Generate one with: openssl rand -base64 64");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }
    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("merchantId", user.getMerchantId() == null ? null : user.getMerchantId().toString())
                .claim("roles", List.copyOf(user.roleNames()))
                .claim("permissions", List.copyOf(user.permissionValues()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }
}

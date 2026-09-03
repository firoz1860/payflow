package com.payflow.gateway.config;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;
@Configuration
public class RateLimitConfig {
    @Bean("apiKeyOrIpKeyResolver")
    @Primary
    public KeyResolver apiKeyOrIpKeyResolver() {
        return exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String credential = authorization.substring(7).trim();
                if (credential.length() > 12) {
                    return Mono.just("key:" + Integer.toHexString(credential.hashCode()));
                }
            }
            return Mono.just("ip:" + clientIp(exchange));
        };
    }
    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }
    private String clientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}

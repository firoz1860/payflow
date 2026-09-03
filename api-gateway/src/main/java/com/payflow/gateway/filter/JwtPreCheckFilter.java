package com.payflow.gateway.filter;
import com.payflow.gateway.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Component
@ConfigurationProperties(prefix = "payflow.gateway")
public class JwtPreCheckFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(JwtPreCheckFilter.class);
    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private final JwtVerifier jwtVerifier;
    private List<String> publicPaths = List.of();
    public JwtPreCheckFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }
    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (publicPaths.stream().anyMatch(p -> MATCHER.match(p, path))) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(exchange, "Missing bearer credentials");
        }
        String credential = authorization.substring(7).trim();
        if (credential.startsWith("sk_") || credential.startsWith("pk_")) {
            return chain.filter(exchange);
        }
        try {
            Claims claims = jwtVerifier.verify(credential);
            var mutated = exchange.getRequest().mutate()
                    .header("X-PayFlow-User-Id", claims.getSubject())
                    .header("X-PayFlow-Merchant-Id", string(claims.get("merchantId")))
                    .header("X-PayFlow-Permissions", string(claims.get("permissions")))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token at gateway: {}", ex.getMessage());
            return reject(exchange, "Invalid or expired token");
        }
    }
    private String string(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }
    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}

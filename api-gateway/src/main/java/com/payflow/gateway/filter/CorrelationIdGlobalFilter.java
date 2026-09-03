package com.payflow.gateway.filter;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Correlation-Id";
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = sanitize(exchange.getRequest().getHeaders().getFirst(HEADER));
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(exchange.mutate().request(mutated).build())
                .contextWrite(ctx -> ctx.put(HEADER, correlationId))
                .doFirst(() -> MDC.put("correlationId", correlationId))
                .doFinally(signal -> MDC.remove("correlationId"));
    }
    private String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 64) {
            return "cid_" + UUID.randomUUID().toString().replace("-", "");
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                return "cid_" + UUID.randomUUID().toString().replace("-", "");
            }
        }
        return candidate;
    }
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

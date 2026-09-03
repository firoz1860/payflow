package com.payflow.gateway.filter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
@Component
public class InternalPathBlockFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean gatewayOwnActuator = path.startsWith("/actuator/health");
        if ((path.startsWith("/internal/") || path.startsWith("/actuator/")) && !gatewayOwnActuator) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange.mutate().request(exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-PayFlow-User-Id");
                    h.remove("X-PayFlow-Merchant-Id");
                    h.remove("X-PayFlow-Permissions");
                    h.remove("X-Internal-Token");
                })
                .build()).build());
    }
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

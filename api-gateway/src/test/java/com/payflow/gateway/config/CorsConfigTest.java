package com.payflow.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {
    private static final String ORIGIN = "https://payflow-b5r1.vercel.app";

    @Test
    void healthFailureRetainsCorsHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://gateway.example/actuator/health")
                .header("Origin", ORIGIN));
        new CorsConfig().corsWebFilter(ORIGIN).filter(exchange, request -> {
            request.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return Mono.empty();
        }).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
    }

    @Test
    void registrationPreflightDoesNotCallDownstream() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.options("https://gateway.example/api/v1/auth/register")
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"));
        new CorsConfig().corsWebFilter(ORIGIN).filter(exchange,
                request -> Mono.error(new AssertionError("Preflight reached downstream"))).block();
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
    }

    @Test
    void unknownOriginIsRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://gateway.example/actuator/health")
                .header("Origin", "https://untrusted.example"));
        new CorsConfig().corsWebFilter(ORIGIN).filter(exchange,
                request -> Mono.error(new AssertionError("Untrusted origin reached downstream"))).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin()).isNull();
    }
}

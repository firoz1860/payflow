package com.payflow.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "PAYFLOW_CORS_ORIGINS=https://payflow-b5r1.vercel.app,https://online-payflow.vercel.app"
})
class GatewayRuntimeTest {
    @Autowired Environment environment;
    @Autowired com.payflow.gateway.filter.JwtPreCheckFilter jwtFilter;

    @Test
    void guestAndPublicContentBypassJwtButAdminAndPaymentsRemainProtected() {
        for (String path : new String[]{"/api/v1/auth/guest", "/api/v1/auth/site-content/hero",
                "/api/v1/auth/admin/site-content/hero", "/api/v1/payments"}) {
            var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                    org.springframework.mock.http.server.reactive.MockServerHttpRequest.get(path));
            var forwarded = new java.util.concurrent.atomic.AtomicBoolean();
            jwtFilter.filter(exchange, e -> { forwarded.set(true); return reactor.core.publisher.Mono.empty(); }).block();
            boolean isPublic = path.equals("/api/v1/auth/guest") || path.equals("/api/v1/auth/site-content/hero");
            assertEquals(isPublic, forwarded.get(), path);
            if (!isPublic) assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }


    @Test
    void startsAndServesLivenessWithItsActualDependencyGraph() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/actuator/health/liveness"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
    }

    @Test
    void allowsBothProductionOriginsAndRejectsAnUntrustedOrigin() throws Exception {
        for (var origin : new String[]{"https://payflow-b5r1.vercel.app", "https://online-payflow.vercel.app",
                "https://untrusted.example"}) {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (origin.equals("https://untrusted.example")) {
                assertEquals(403, response.statusCode());
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            } else {
                assertEquals(200, response.statusCode());
                assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
            }
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + path);
    }
}

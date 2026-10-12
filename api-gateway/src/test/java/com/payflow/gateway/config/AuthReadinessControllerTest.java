package com.payflow.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthReadinessControllerTest {
    @Test void productionDefaultsAllowSlowHealthyServicesInParallel() throws Exception {
        var count = new AtomicInteger();
        try (var auth = new Service(200, "UP", count, 4000); var merchant = new Service(200, "UP", count, 4000)) {
            long started = System.nanoTime();
            var response = new AuthReadinessController(auth.uri().toString(), merchant.uri().toString())
                    .readiness().block(Duration.ofSeconds(25));
            assertTrue(response.getBody().ready());
            assertEquals(2, count.get());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(8)) < 0);
        }
    }

    @Test void requiresBothServicesAndCachesProbesWithoutExposingInfrastructure() throws Exception {
        var count = new AtomicInteger();
        try (var auth = new Service(200, "UP", count); var merchant = new Service(200, "UP", count)) {
            var controller = new AuthReadinessController(WebClient.builder().build(), auth.uri(), merchant.uri(),
                    Duration.ofSeconds(1), Duration.ofSeconds(3));
            var first = controller.readiness().block();
            var second = controller.readiness().block();
            assertTrue(first.getBody().ready());
            assertTrue(second.getBody().ready());
            assertEquals(2, count.get());
            assertEquals("no-store", first.getHeaders().getCacheControl());
        }
    }
    @Test void anUnhealthyServiceOrHttpFailureIsNeverReady() throws Exception {
        for (var failure : new Object[][]{{200, "DOWN"}, {503, "UP"}}) {
            try (var auth = new Service(200, "UP", new AtomicInteger());
                 var merchant = new Service((int)failure[0], (String)failure[1], new AtomicInteger())) {
                var controller = new AuthReadinessController(WebClient.builder().build(), auth.uri(), merchant.uri(),
                        Duration.ofSeconds(1), Duration.ZERO);
                assertFalse(controller.readiness().block().getBody().ready());
            }
        }
    }
    @Test void aHangingServiceProducesABoundedNotReadyResponse() {
        StepVerifier.withVirtualTime(() -> {
            var client = WebClient.builder().exchangeFunction(request ->
                    request.url().getHost().equals("auth.example.test")
                    ? Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                            .body("{\"status\":\"UP\"}").build()) : Mono.never()).build();
            return new AuthReadinessController(client, URI.create("https://auth.example.test"),
                    URI.create("https://merchant.example.test"), Duration.ofSeconds(3), Duration.ZERO).readiness();
        }).expectSubscription().thenAwait(Duration.ofSeconds(3))
                .assertNext(response -> assertFalse(response.getBody().ready())).verifyComplete();
    }
    @Test void aCachedFailureExpiresAndReflectsServiceRecovery() {
        var healthy = new java.util.concurrent.atomic.AtomicBoolean();
        var count = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> {
            var client = WebClient.builder().exchangeFunction(request -> {
                count.incrementAndGet();
                return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                        .body(healthy.get() ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}").build());
            }).build();
            var controller = new AuthReadinessController(client, URI.create("https://auth.example.test"),
                    URI.create("https://merchant.example.test"), Duration.ofSeconds(3), Duration.ofSeconds(3));
            return Flux.concat(controller.readiness(), Mono.fromRunnable(() -> healthy.set(true)).then(controller.readiness()),
                    Mono.delay(Duration.ofSeconds(4)).then(controller.readiness()));
        }).expectSubscription().assertNext(response -> assertFalse(response.getBody().ready()))
                .assertNext(response -> { assertFalse(response.getBody().ready()); assertEquals(2, count.get()); })
                .thenAwait(Duration.ofSeconds(4)).assertNext(response -> assertTrue(response.getBody().ready())).verifyComplete();
        assertEquals(4, count.get());
    }
    @Test void concurrentCallersShareTheSameInFlightProbes() {
        var count = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> {
            var client = WebClient.builder().exchangeFunction(request -> {
                count.incrementAndGet();
                return Mono.delay(Duration.ofSeconds(1)).map(ignored -> ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body("{\"status\":\"UP\"}").build());
            }).build();
            var controller = new AuthReadinessController(client, URI.create("https://auth.example.test"),
                    URI.create("https://merchant.example.test"), Duration.ofSeconds(3), Duration.ofSeconds(3));
            return Flux.merge(controller.readiness(), controller.readiness());
        }).expectSubscription().thenAwait(Duration.ofSeconds(1))
                .assertNext(response -> assertTrue(response.getBody().ready()))
                .assertNext(response -> assertTrue(response.getBody().ready())).verifyComplete();
        assertEquals(2, count.get());
    }
    @Test void cancellingOneCallerDoesNotCancelSharedWarmupProbes() {
        var count = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> {
            var client = WebClient.builder().exchangeFunction(request -> {
                count.incrementAndGet();
                return Mono.delay(Duration.ofSeconds(6)).map(ignored -> ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json").body("{\"status\":\"UP\"}").build());
            }).build();
            var controller = new AuthReadinessController(client, URI.create("https://auth.example.test"),
                    URI.create("https://merchant.example.test"), Duration.ofSeconds(20), Duration.ofSeconds(3));
            var firstCaller = controller.readiness().subscribe();
            firstCaller.dispose();
            return Mono.delay(Duration.ofSeconds(1)).then(controller.readiness());
        }).expectSubscription().thenAwait(Duration.ofSeconds(6))
                .assertNext(response -> assertTrue(response.getBody().ready())).verifyComplete();
        assertEquals(2, count.get());
    }
    static class Service implements AutoCloseable {
        private final HttpServer server;
        Service(int status, String health, AtomicInteger count) throws Exception {
            this(status, health, count, 0);
        }
        Service(int status, String health, AtomicInteger count, long delayMillis) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/actuator/health", exchange -> {
                count.incrementAndGet();
                java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(delayMillis).toNanos());
                byte[] body = ("{\"status\":\"" + health + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
        }
        URI uri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
        public void close() { server.stop(0); }
    }
}

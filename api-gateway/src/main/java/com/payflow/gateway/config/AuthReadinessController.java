package com.payflow.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

@RestController
public class AuthReadinessController {
    private static final Logger log = LoggerFactory.getLogger(AuthReadinessController.class);
    public record Readiness(boolean ready) {}
    private final Mono<Readiness> readiness;
    @Autowired
    public AuthReadinessController(@Value("${AUTH_SERVICE_URL:http://${AUTH_SERVICE_HOSTPORT:localhost:8081}}") String auth,
            @Value("${MERCHANT_SERVICE_URL:http://${MERCHANT_SERVICE_HOSTPORT:localhost:8082}}") String merchant) {
        this(WebClient.builder().build(), URI.create(auth), URI.create(merchant), Duration.ofSeconds(20), Duration.ofSeconds(3));
    }
    AuthReadinessController(WebClient client, URI auth, URI merchant, Duration timeout, Duration cache) {
        for (URI origin : new URI[]{auth, merchant}) {
            if (!("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
                    || origin.getHost() == null || origin.getUserInfo() != null) {
                throw new IllegalArgumentException("Readiness requires HTTP service origins");
            }
        }
        // Probes run only on demand. Share each bounded result briefly across callers.
        this.readiness = Mono.defer(() -> Mono.zip(probe(client, auth, timeout, "auth"), probe(client, merchant, timeout, "merchant"))
                .map(results -> new Readiness(results.getT1() && results.getT2()))).cache(cache);
    }
    @GetMapping("/api/v1/auth/readiness")
    public Mono<ResponseEntity<Readiness>> readiness() {
        return readiness.map(result -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result));
    }
    private Mono<Boolean> probe(WebClient client, URI origin, Duration timeout, String service) {
        return client.get().uri(origin.resolve("/actuator/health")).retrieve().bodyToMono(Map.class)
                .map(body -> "UP".equals(body.get("status"))).defaultIfEmpty(false)
                .timeout(timeout).onErrorResume(error -> {
                    int status = error instanceof WebClientResponseException response ? response.getStatusCode().value() : -1;
                    log.debug("Authentication readiness probe for {} failed ({}, HTTP {})", service, error.getClass().getSimpleName(), status);
                    return Mono.just(false);
                });
    }
}

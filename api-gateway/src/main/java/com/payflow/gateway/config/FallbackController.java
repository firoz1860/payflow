package com.payflow.gateway.config;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Map;
@RestController
public class FallbackController {
    @RequestMapping("/fallback/service-unavailable")
    public Mono<ResponseEntity<Map<String, Object>>> serviceUnavailable() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "20")
                .body(Map.of(
                        "code", "PROVIDER_UNAVAILABLE",
                        "message", "The service is temporarily unavailable. Retry with the same Idempotency-Key.",
                        "timestamp", Instant.now().toString())));
    }
    @RequestMapping("/fallback/not-implemented")
    public Mono<ResponseEntity<Map<String, Object>>> notImplemented() {
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "code", "NOT_IMPLEMENTED",
                "message", "Merchant webhook endpoint management is not available in this deployment.",
                "timestamp", Instant.now().toString())));
    }

}

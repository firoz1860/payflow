package com.payflow.payment.integration;
import com.payflow.payment.service.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class IdempotencyIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    private IdempotencyService idempotencyService;
    @Test
    @DisplayName("the same key with the same body replays instead of re-executing")
    void sameKeySameBodyReplays() {
        UUID merchantId = UUID.randomUUID();
        String key = "checkout-" + UUID.randomUUID();
        String body = "{\"amount\":1000,\"currency\":\"INR\"}";
        var first = idempotencyService.claim(merchantId, key, "POST /api/v1/payments", body);
        assertThat(first).isInstanceOf(IdempotencyService.Claim.Acquired.class);
        idempotencyService.complete(((IdempotencyService.Claim.Acquired) first).recordId(),
                "pay_abc", 201, "{\"paymentReference\":\"pay_abc\"}");
        var second = idempotencyService.claim(merchantId, key, "POST /api/v1/payments", body);
        assertThat(second).isInstanceOf(IdempotencyService.Claim.Replay.class);
        var replay = (IdempotencyService.Claim.Replay) second;
        assertThat(replay.resourceId()).isEqualTo("pay_abc");
        assertThat(replay.responseCode()).isEqualTo(201);
    }
    @Test
    @DisplayName("the same key with a different body is a conflict, never a silent replay")
    void sameKeyDifferentBodyConflicts() {
        UUID merchantId = UUID.randomUUID();
        String key = "checkout-" + UUID.randomUUID();
        var first = idempotencyService.claim(merchantId, key, "POST /api/v1/payments",
                "{\"amount\":1000}");
        idempotencyService.complete(((IdempotencyService.Claim.Acquired) first).recordId(),
                "pay_abc", 201, "{}");
        assertThatThrownBy(() -> idempotencyService.claim(merchantId, key,
                "POST /api/v1/payments", "{\"amount\":9999}"))
                .isInstanceOf(com.payflow.common.error.PayFlowException.class)
                .hasMessageContaining("different request body");
    }
    @Test
    @DisplayName("the same key is scoped per merchant and never leaks across tenants")
    void keysAreScopedPerMerchant() {
        String key = "shared-key";
        var a = idempotencyService.claim(UUID.randomUUID(), key, "POST /api/v1/payments", "{}");
        var b = idempotencyService.claim(UUID.randomUUID(), key, "POST /api/v1/payments", "{}");
        assertThat(a).isInstanceOf(IdempotencyService.Claim.Acquired.class);
        assertThat(b).isInstanceOf(IdempotencyService.Claim.Acquired.class);
    }
    @Test
    @DisplayName("under concurrency exactly one caller acquires the claim")
    void concurrentClaimsElectOneWinner() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String key = "race-" + UUID.randomUUID();
        String body = "{\"amount\":1000}";
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger acquired = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, threads)
                .<Callable<Void>>mapToObj(i -> () -> {
                    try {
                        var claim = idempotencyService.claim(merchantId, key,
                                "POST /api/v1/payments", body);
                        if (claim instanceof IdempotencyService.Claim.Acquired) {
                            acquired.incrementAndGet();
                        }
                    } catch (RuntimeException ex) {
                        rejected.incrementAndGet();   // 409 "currently being processed"
                    }
                    return null;
                })
                .toList();
        for (Future<Void> future : pool.invokeAll(tasks)) {
            future.get();
        }
        pool.shutdown();
        assertThat(acquired.get()).isEqualTo(1);
        assertThat(acquired.get() + rejected.get()).isEqualTo(threads);
    }
}

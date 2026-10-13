package com.payflow.payment.integration;
import com.payflow.payment.service.IdempotencyService;
import com.payflow.payment.repository.IdempotencyRecordRepository;
import com.payflow.common.error.PayFlowException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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
    @Autowired private IdempotencyRecordRepository records;
    @Autowired private JdbcTemplate jdbc;
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
        CyclicBarrier ready = new CyclicBarrier(threads);
        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, threads)
                .<Callable<Void>>mapToObj(i -> () -> {
                    ready.await(30, TimeUnit.SECONDS);
                    try {
                        var claim = idempotencyService.claim(merchantId, key,
                                "POST /api/v1/payments", body);
                        if (claim instanceof IdempotencyService.Claim.Acquired) {
                            acquired.incrementAndGet();
                        }
                    } catch (PayFlowException ex) {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        rejected.incrementAndGet();
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

    @Test
    void aStaleClaimIsUnknownAndCannotBeTakenOver() {
        UUID merchant = UUID.randomUUID();
        String key = "stale-" + UUID.randomUUID();
        var claim = (IdempotencyService.Claim.Acquired) idempotencyService.claim(merchant, key, "POST /api/v1/payments", "{}");
        jdbc.update("UPDATE idempotency_records SET created_at=now()-interval '1 hour' WHERE id=?", claim.recordId());
        var before = records.findById(claim.recordId()).orElseThrow().getCreatedAt();
        assertThatThrownBy(() -> idempotencyService.claim(merchant, key, "POST /api/v1/payments", "{}"))
                .isInstanceOfSatisfying(PayFlowException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode().name()).isEqualTo("PAYMENT_CREATION_UNCERTAIN");
                });
        assertThat(records.findById(claim.recordId()).orElseThrow().getCreatedAt()).isEqualTo(before);
    }

    @Test
    void failedClaimsStillRejectAChangedBody() {
        UUID merchant = UUID.randomUUID();
        String key = "failed-body-" + UUID.randomUUID();
        var claim = (IdempotencyService.Claim.Acquired) idempotencyService.claim(merchant, key, "POST /api/v1/payments", "{\"amount\":10}");
        idempotencyService.fail(claim.recordId());
        assertThatThrownBy(() -> idempotencyService.claim(merchant, key, "POST /api/v1/payments", "{\"amount\":20}"))
                .isInstanceOfSatisfying(PayFlowException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode().name()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
    }

    @Test
    void expiryCleanupKeepsUnresolvedClaimsAndDeletesOnlyCompletedReplays() {
        UUID merchant = UUID.randomUUID();
        var failed = (IdempotencyService.Claim.Acquired) idempotencyService.claim(merchant, "failed-" + UUID.randomUUID(), "POST /api/v1/payments", "{}");
        var pending = (IdempotencyService.Claim.Acquired) idempotencyService.claim(merchant, "pending-" + UUID.randomUUID(), "POST /api/v1/payments", "{}");
        var completed = (IdempotencyService.Claim.Acquired) idempotencyService.claim(merchant, "done-" + UUID.randomUUID(), "POST /api/v1/payments", "{}");
        idempotencyService.fail(failed.recordId());
        idempotencyService.complete(completed.recordId(), "pay_completed", 201, "{}");
        jdbc.update("UPDATE idempotency_records SET expires_at=now()-interval '1 hour' WHERE id IN (?, ?, ?)", failed.recordId(), pending.recordId(), completed.recordId());
        idempotencyService.purgeExpired();
        assertThat(records.findById(failed.recordId())).isPresent();
        assertThat(records.findById(pending.recordId())).isPresent();
        assertThat(records.findById(completed.recordId())).isEmpty();
    }
}

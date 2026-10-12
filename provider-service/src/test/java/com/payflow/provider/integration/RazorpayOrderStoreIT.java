package com.payflow.provider.integration;
import com.payflow.provider.gateway.razorpay.JdbcRazorpayOrderAttemptStore;
import com.payflow.common.error.PayFlowException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
@Testcontainers
class RazorpayOrderStoreIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc() {
        String url = postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=provider";
        Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword()).schemas("provider").load().migrate();
        return new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
    }
    @Test void lostCreateResponseDoesNotCreateAgain() {
        var store = new JdbcRazorpayOrderAttemptStore(jdbc());
        String key = UUID.randomUUID().toString(), reference = UUID.randomUUID().toString();
        assertTrue(store.claim(key, reference, "hash").acquired());
        store.uncertain(key);
        assertThrows(PayFlowException.class, () -> store.claim(key, reference, "hash"));
        assertThrows(PayFlowException.class, () -> store.claim(key, "other-reference", "hash"));
    }
    @Test void concurrentCreateAcquiresOneDurableClaimAndCompletedReplay() throws Exception {
        var database = jdbc();
        var store = new JdbcRazorpayOrderAttemptStore(database);
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < 25; round++) {
                String key = UUID.randomUUID().toString(), reference = UUID.randomUUID().toString();
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var a = executor.submit(() -> { ready.countDown(); start.await(); return acquired(store, key, reference); });
                var b = executor.submit(() -> { ready.countDown(); start.await(); return acquired(store, key, reference); });
                try { assertTrue(ready.await(5, TimeUnit.SECONDS)); }
                finally { start.countDown(); }
                assertEquals(1, a.get(5, TimeUnit.SECONDS) + b.get(5, TimeUnit.SECONDS));
                assertEquals(1, database.queryForObject("SELECT count(*) FROM razorpay_order_attempts WHERE payment_reference=?", Integer.class, reference));
                store.complete(key, "{\"orderId\":\"order_one\"}");
                assertFalse(store.claim(key, reference, "hash").acquired());
                assertTrue(store.claim(key, reference, "hash").responseJson().contains("order_one"));
            }
        }
    }
    @Test void differentKeyCannotClaimAnExistingPaymentReference() {
        var database = jdbc();
        var store = new JdbcRazorpayOrderAttemptStore(database);
        String original = UUID.randomUUID().toString(), other = UUID.randomUUID().toString(), reference = UUID.randomUUID().toString();
        assertTrue(store.claim(original, reference, "hash").acquired());
        assertEquals(HttpStatus.CONFLICT, assertThrows(PayFlowException.class, () -> store.claim(other, reference, "hash")).getStatus());
        assertEquals(0, database.queryForObject("SELECT count(*) FROM razorpay_order_attempts WHERE key_hash=?", Integer.class, other));
        store.complete(original, "{\"orderId\":\"original_order\"}");
        assertEquals(HttpStatus.CONFLICT, assertThrows(PayFlowException.class, () -> store.claim(other, reference, "hash")).getStatus());
        assertEquals(HttpStatus.CONFLICT, assertThrows(PayFlowException.class, () -> store.claim(original, reference, "changed-body")).getStatus());
        assertTrue(store.claim(original, reference, "hash").responseJson().contains("original_order"));
    }
    private int acquired(JdbcRazorpayOrderAttemptStore store, String key, String reference) {
        try { return store.claim(key, reference, "hash").acquired() ? 1 : 0; }
        catch (PayFlowException ex) { assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus()); return 0; }
    }
}

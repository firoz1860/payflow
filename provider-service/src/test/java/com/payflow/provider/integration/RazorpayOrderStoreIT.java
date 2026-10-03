package com.payflow.provider.integration;
import com.payflow.provider.gateway.razorpay.JdbcRazorpayOrderAttemptStore;
import com.payflow.common.error.PayFlowException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import java.util.concurrent.Executors;
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
        var store = new JdbcRazorpayOrderAttemptStore(jdbc());
        String key = UUID.randomUUID().toString(), reference = UUID.randomUUID().toString();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> acquired(store, key, reference));
            var b = executor.submit(() -> acquired(store, key, reference));
            assertEquals(1, a.get() + b.get());
        }
        store.complete(key, "{\"orderId\":\"order_one\"}");
        assertFalse(store.claim(key, reference, "hash").acquired());
        assertTrue(store.claim(key, reference, "hash").responseJson().contains("order_one"));
    }
    private int acquired(JdbcRazorpayOrderAttemptStore store, String key, String reference) {
        try { return store.claim(key, reference, "hash").acquired() ? 1 : 0; }
        catch (PayFlowException ex) { return 0; }
    }
}

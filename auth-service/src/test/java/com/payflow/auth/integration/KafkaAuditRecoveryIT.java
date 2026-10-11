package com.payflow.auth.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class KafkaAuditRecoveryIT {
    @Test void requeuesOnlyUnpublishedAuditEventsFromTheKafkaUpgradeIncident() {
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            String baseUrl = postgres.getJdbcUrl();
            String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=auth";
            Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword()).schemas("auth").target("7").load().migrate();
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
            UUID affected = insert(jdbc, "audit.event", "FAILED", "Failed to construct kafka producer", "2026-10-11T16:00:00Z");
            UUID old = insert(jdbc, "audit.event", "FAILED", "Failed to construct kafka producer", "2026-10-10T16:00:00Z");
            UUID notification = insert(jdbc, "notification.requested", "FAILED", "Failed to construct kafka producer", "2026-10-11T16:00:00Z");
            UUID unrelated = insert(jdbc, "audit.event", "FAILED", "Broker unavailable", "2026-10-11T16:00:00Z");
            UUID published = insert(jdbc, "audit.event", "PUBLISHED", "Failed to construct kafka producer", "2026-10-11T16:00:00Z");
            Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword()).schemas("auth").load().migrate();
            assertEquals("PENDING", status(jdbc, affected));
            assertEquals(0, jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE id=?", Integer.class, affected));
            assertNull(jdbc.queryForObject("SELECT last_error FROM outbox_events WHERE id=?", String.class, affected));
            assertEquals(1L, jdbc.queryForObject("SELECT version FROM outbox_events WHERE id=?", Long.class, affected));
            for (UUID id : new UUID[]{old, notification, unrelated}) assertEquals("FAILED", status(jdbc, id));
            assertEquals("PUBLISHED", status(jdbc, published));
        }
    }
    private UUID insert(JdbcTemplate jdbc, String type, String status, String error, String created) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,partition_key,payload,status,attempts,last_error,created_at) VALUES(?,'User','test',?,'test','{}'::jsonb,?,10,?,?::timestamptz)", id, type, status, error, created);
        return id;
    }
    private String status(JdbcTemplate jdbc, UUID id) {
        return jdbc.queryForObject("SELECT status FROM outbox_events WHERE id=?", String.class, id);
    }
}

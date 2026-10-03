package com.payflow.auth.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class LedgerRolePermissionIT {
    @Test void forwardMigrationGrantsOwnerLedgerReadWithoutBroadeningDeveloperOrSupport() {
        try (var postgres=new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            String url=postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?") ? "&" : "?")+"currentSchema=auth";
            Flyway.configure().dataSource(url,postgres.getUsername(),postgres.getPassword()).schemas("auth").load().migrate();
            var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,postgres.getUsername(),postgres.getPassword()));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM roles r JOIN role_permissions p ON p.role_id=r.id WHERE r.name='MERCHANT_OWNER' AND p.permission='LEDGER_READ'",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM roles r JOIN role_permissions p ON p.role_id=r.id WHERE r.name IN ('MERCHANT_DEVELOPER','MERCHANT_SUPPORT') AND p.permission='LEDGER_READ'",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM roles r JOIN role_permissions p ON p.role_id=r.id WHERE r.name IN ('PAYFLOW_ADMIN','MERCHANT_FINANCE') AND p.permission='LEDGER_READ'",Integer.class));
        }
    }
}

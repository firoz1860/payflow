package com.payflow.ledger.integration;

import com.payflow.ledger.repository.LedgerRepositories.LedgerAccountRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerRepositories.LedgerPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
class SchemaValidationIT {

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private LedgerPostingRepository postingRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void startsAgainstItsFlywayManagedSchemaWithNestedRepositoriesRegistered() {
        assertThat(accountRepository).isNotNull();
        assertThat(postingRepository).isNotNull();
        assertThat(entryRepository).isNotNull();
    }
}

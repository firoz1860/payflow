package com.payflow.merchant.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import com.payflow.common.outbox.OutboxPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "payflow.outbox.enabled=true")
@Testcontainers
@ActiveProfiles("test")
class SchemaValidationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    StringRedisTemplate redis;

    @Autowired
    ApplicationContext context;

    @Test
    void startsAgainstItsFlywayManagedSchema() {
    }

    @Test
    void startsOutboxPublisherWithAutoConfiguredKafkaTemplate() {
        assertThat(context.getBeansOfType(OutboxPublisher.class)).hasSize(1);
    }
}

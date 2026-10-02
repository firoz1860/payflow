package com.payflow.payment.integration;

import com.payflow.common.outbox.OutboxEvent;
import com.payflow.common.outbox.OutboxEventRepository;
import com.payflow.common.outbox.OutboxRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"payflow.outbox.enabled=true",
        "payflow.outbox.shared-topic=payment.created",
        "spring.kafka.listener.auto-startup=false"})
@Testcontainers
@ActiveProfiles("test")
class OutboxDeliveryIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
    @Autowired OutboxRecorder recorder;
    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void scheduledPublisherDeliversCommittedEventToSharedTopic() {
        UUID id = new TransactionTemplate(transactions).execute(status ->
                recorder.record("Payment", UUID.randomUUID().toString(), "audit.event", 1,
                        Map.of("action", "TEST")).getId());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findById(id).orElseThrow().getStatus())
                        .isEqualTo(OutboxEvent.Status.PUBLISHED));
    }
}

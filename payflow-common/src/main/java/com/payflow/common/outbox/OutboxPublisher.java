package com.payflow.common.outbox;
import com.payflow.common.web.CorrelationId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.concurrent.TimeUnit;
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           OutboxProperties properties,
                           MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.publishedCounter = Counter.builder("payflow.outbox.published").register(meterRegistry);
        this.failedCounter = Counter.builder("payflow.outbox.publish.failed").register(meterRegistry);
    }
    @Scheduled(fixedDelayString = "${payflow.outbox.poll-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        if (!properties.isEnabled()) {
            return;
        }
        List<OutboxEvent> batch = repository.lockBatchByStatus(
                OutboxEvent.Status.PENDING, PageRequest.of(0, properties.getBatchSize()));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        event.getEventType(), event.getPartitionKey(), event.getPayload());
                record.headers().add("eventId", event.getId().toString().getBytes());
                record.headers().add("eventType", event.getEventType().getBytes());
                if (event.getCorrelationId() != null) {
                    record.headers().add(CorrelationId.HEADER, event.getCorrelationId().getBytes());
                }
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                event.markPublished();
                publishedCounter.increment();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                event.markFailed("Publisher interrupted");
                failedCounter.increment();
                repository.saveAll(batch);
                return;
            } catch (Exception ex) {
                event.markFailed(ex.getMessage());
                failedCounter.increment();
                log.error("Outbox publish failed for event {} type {} (attempt {})",
                        event.getId(), event.getEventType(), event.getAttempts(), ex);
            }
        }
        repository.saveAll(batch);
    }
}

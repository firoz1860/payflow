package com.payflow.common.outbox;
import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class OutboxTopicRoutingTest {
    @Test
    void defaultRoutingPreservesTopicPerEvent() {
        assertThat(new OutboxProperties().topicFor("audit.event")).isEqualTo("audit.event");
    }
    @Test
    void sharedRoutingKeepsDirectTopicsAndEventIdentity() throws Exception {
        var properties = new OutboxProperties();
        properties.setSharedTopic("payment.created");
        properties.setDirectTopics(Set.of("payment.captured", "provider.payment.updated"));
        assertThat(properties.topicFor("payment.captured")).isEqualTo("payment.captured");
        assertThat(properties.topicFor("audit.event")).isEqualTo("payment.created");
        var repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String,String> kafka = mock(KafkaTemplate.class);
        var event = new OutboxEvent("Merchant", "123", "audit.event", "merchant-123", "{}", null);
        when(repository.lockBatchByStatus(eq(OutboxEvent.Status.PENDING), any())).thenReturn(List.of(event));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        new OutboxPublisher(repository, kafka, properties, new SimpleMeterRegistry()).publishPending();
        ArgumentCaptor<ProducerRecord<String,String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        var record = captor.getValue();
        assertThat(record.topic()).isEqualTo("payment.created");
        assertThat(record.key()).isEqualTo("merchant-123");
        assertThat(new String(record.headers().lastHeader("eventType").value())).isEqualTo("audit.event");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
    }
}

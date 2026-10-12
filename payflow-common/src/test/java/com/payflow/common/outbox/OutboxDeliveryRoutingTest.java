package com.payflow.common.outbox;

import com.payflow.common.event.Topics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxDeliveryRoutingTest {
    @Test void handlesNotificationsWithTheConfiguredDeliveryInsteadOfKafka() {
        var event = notification();
        var repo = repository(event);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var delivered = new AtomicInteger();
        var delivery = new OutboxDelivery() {
            public boolean supports(OutboxEvent candidate) { return Topics.NOTIFICATION_REQUESTED.equals(candidate.getEventType()); }
            public void publish(OutboxEvent candidate) { assertThat(candidate.getId()).isEqualTo(event.getId()); delivered.incrementAndGet(); }
        };
        new OutboxPublisher(repo, kafka, new OutboxProperties(), new SimpleMeterRegistry(), List.of(delivery)).publishPending();
        assertThat(delivered.get()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        verifyNoInteractions(kafka);
    }

    @Test void failedEmailDeliveryStaysPendingWithoutFallingBackToKafka() {
        var event = notification();
        var repo = repository(event);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var delivery = new OutboxDelivery() {
            public boolean supports(OutboxEvent candidate) { return true; }
            public void publish(OutboxEvent candidate) { throw new IllegalStateException("Email provider unavailable"); }
        };
        new OutboxPublisher(repo, kafka, new OutboxProperties(), new SimpleMeterRegistry(), List.of(delivery)).publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        verifyNoInteractions(kafka);
    }

    @Test void ordinaryEventsContinueToUseKafkaWhenNoDeliveryClaimsThem() {
        var event = new OutboxEvent("User", "test", Topics.AUDIT_EVENT, "test", "{}", null);
        var repo = repository(event);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var delivery = new OutboxDelivery() {
            public boolean supports(OutboxEvent candidate) { return false; }
            public void publish(OutboxEvent candidate) { throw new AssertionError("Unclaimed audit event routed to mail"); }
        };
        new OutboxPublisher(repo, kafka, new OutboxProperties(), new SimpleMeterRegistry(), List.of(delivery)).publishPending();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
        verify(kafka).send(any(org.apache.kafka.clients.producer.ProducerRecord.class));
    }

    private OutboxEvent notification() { return new OutboxEvent("User", "test", Topics.NOTIFICATION_REQUESTED, "test", "{}", null); }
    private OutboxEventRepository repository(OutboxEvent event) {
        var repo = mock(OutboxEventRepository.class);
        when(repo.lockBatchByStatus(any(), any())).thenReturn(List.of(event));
        return repo;
    }
}

package com.payflow.common.outbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.event.EventEnvelope;
import com.payflow.common.web.CorrelationId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Component
public class OutboxRecorder {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    public OutboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String aggregateType, String aggregateId, String eventType,
                              int version, Object data) {
        return record(aggregateType, aggregateId, eventType, version, aggregateId, data);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent record(String aggregateType, String aggregateId, String eventType,
                              int version, String partitionKey, Object data) {
        String correlationId = CorrelationId.get();
        EventEnvelope<Object> envelope = EventEnvelope.of(
                eventType, version, aggregateType, aggregateId, correlationId, data);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialise outbox payload for " + eventType, ex);
        }
        return repository.save(new OutboxEvent(
                aggregateType, aggregateId, eventType, partitionKey, payload, correlationId));
    }
}

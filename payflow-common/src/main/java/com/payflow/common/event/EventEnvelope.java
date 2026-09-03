package com.payflow.common.event;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int version,
        String aggregateType,
        String aggregateId,
        String correlationId,
        Instant occurredAt,
        T data
) {
    public static <T> EventEnvelope<T> of(String eventType, int version, String aggregateType,
                                          String aggregateId, String correlationId, T data) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, version,
                aggregateType, aggregateId, correlationId, Instant.now(), data);
    }
}

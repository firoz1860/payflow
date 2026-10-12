package com.payflow.common.outbox;

/** Optional delivery for an event type; unclaimed events keep using Kafka. */
public interface OutboxDelivery {
    boolean supports(OutboxEvent event);
    void publish(OutboxEvent event) throws Exception;
}

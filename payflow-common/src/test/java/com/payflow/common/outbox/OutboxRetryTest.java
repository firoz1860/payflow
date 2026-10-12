package com.payflow.common.outbox;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryTest {
    @Test void failedEventsWaitBeforeRetryingAndDelayIsBounded() {
        var event = new OutboxEvent("User", "test", "notification.requested", "test", "{}", null);
        assertThat(event.getNextAttemptAt()).isEqualTo(event.getCreatedAt());
        long[] delays = {30, 60, 120, 240, 300, 300, 300, 300, 300};
        for (long delay : delays) {
            Instant before = Instant.now();
            event.markFailed("Unavailable");
            assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
            assertThat(event.getNextAttemptAt()).isBetween(before.plusSeconds(delay), Instant.now().plusSeconds(delay));
        }
        event.markFailed("Unavailable");
        assertThat(event.getAttempts()).isEqualTo(10);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.FAILED);
    }
}

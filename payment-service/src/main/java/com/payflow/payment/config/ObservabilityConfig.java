package com.payflow.payment.config;
import com.payflow.common.outbox.OutboxEvent;
import com.payflow.common.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class ObservabilityConfig {
    @Bean
    public Gauge outboxPendingGauge(MeterRegistry registry, OutboxEventRepository repository) {
        return Gauge.builder("payflow.outbox.pending",
                        () -> repository.countByStatus(OutboxEvent.Status.PENDING))
                .description("Outbox events awaiting publication to Kafka")
                .register(registry);
    }
    @Bean
    public HealthIndicator outboxHealthIndicator(OutboxEventRepository repository) {
        return () -> {
            long pending = repository.countByStatus(OutboxEvent.Status.PENDING);
            long failed = repository.countByStatus(OutboxEvent.Status.FAILED);
            Health.Builder builder = (pending > 5000 || failed > 0) ? Health.down() : Health.up();
            return builder.withDetail("pending", pending).withDetail("failed", failed).build();
        };
    }
}

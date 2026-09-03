package com.payflow.common.autoconfigure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.outbox.OutboxEvent;
import com.payflow.common.outbox.OutboxEventRepository;
import com.payflow.common.outbox.OutboxProperties;
import com.payflow.common.outbox.OutboxPublisher;
import com.payflow.common.outbox.OutboxRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, OutboxEvent.class})
@EnableConfigurationProperties(OutboxProperties.class)
@EntityScan(basePackages = {
        "com.payflow.common.outbox",
        "com.payflow.auth.domain",
        "com.payflow.merchant.domain",
        "com.payflow.payment.domain",
        "com.payflow.provider.domain",
        "com.payflow.ledger.domain"
})
@EnableJpaRepositories(basePackages = {
        "com.payflow.common.outbox",
        "com.payflow.auth.repository",
        "com.payflow.merchant.repository",
        "com.payflow.payment.repository",
        "com.payflow.provider.repository",
        "com.payflow.ledger.repository"
})
@EnableScheduling
public class PayFlowOutboxAutoConfiguration {
    @Bean
    public OutboxRecorder outboxRecorder(OutboxEventRepository repository, ObjectMapper objectMapper) {
        return new OutboxRecorder(repository, objectMapper);
    }
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "payflow.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    public OutboxPublisher outboxPublisher(OutboxEventRepository repository,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           OutboxProperties properties,
                                           MeterRegistry meterRegistry) {
        return new OutboxPublisher(repository, kafkaTemplate, properties, meterRegistry);
    }
}

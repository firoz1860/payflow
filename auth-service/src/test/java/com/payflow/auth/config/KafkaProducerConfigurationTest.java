package com.payflow.auth.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class KafkaProducerConfigurationTest {
    @Test void actualAuthProducerConfigurationCanConstructTheKafkaClient() throws Exception {
        var environment = new StandardEnvironment();
        for (var source : new YamlPropertySourceLoader().load("auth", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        var properties = Binder.get(environment).bind("spring.kafka", KafkaProperties.class).get();
        properties.setBootstrapServers(java.util.List.of("127.0.0.1:9092"));
        assertDoesNotThrow(() -> {
            try (var producer = new KafkaProducer<String, String>(properties.buildProducerProperties())) {
                // Construction validates the effective delivery/request/linger settings.
                // Do not send or wait for any external broker in this unit test.
                producer.close(Duration.ZERO);
            }
        });
    }
}

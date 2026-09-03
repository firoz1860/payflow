package com.payflow.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerDependencyTest {

    @Test
    void hasTheReactiveResilience4jCircuitBreakerImplementationNeededByGatewayRoutes() {
        assertThat(ClassUtils.isPresent(
                "org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory",
                getClass().getClassLoader())).isTrue();
    }
}

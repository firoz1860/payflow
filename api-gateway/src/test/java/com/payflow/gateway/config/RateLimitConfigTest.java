package com.payflow.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitConfigTest {

    @Test
    void apiKeyOrIpResolverIsTheDefaultKeyResolver() {
        try (var context = new AnnotationConfigApplicationContext(RateLimitConfig.class)) {
            assertThat(context.getBean(KeyResolver.class))
                    .isSameAs(context.getBean("apiKeyOrIpKeyResolver", KeyResolver.class));
        }
    }
}

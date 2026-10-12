package com.payflow.auth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.common.outbox.OutboxDelivery;
import com.payflow.common.outbox.OutboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuthEmailConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthEmailConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(OutboxProperties.class, OutboxProperties::new)
            .withBean(OneTimeTokenRepository.class, () -> mock(OneTimeTokenRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class));

    @Test void missingEnableFlagDoesNotCreateAnEmailSenderOrRequireCredentials() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(OutboxDelivery.class));
    }
    @Test void enablingWithoutCredentialsFailsClosed() {
        runner.withPropertyValues("payflow.auth-email.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Authentication email requires a provider key and sender");
        });
    }
    @Test void configuredSenderIsRegisteredAsAnOutboxDelivery() {
        runner.withPropertyValues("payflow.auth-email.enabled=true",
                "payflow.auth-email.api-key=re_unit_test_not_a_real_key", "payflow.auth-email.from=noreply@example.test",
                "payflow.auth-email.frontend-url=https://payflow.example.test")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(OutboxDelivery.class));
    }
    @Test void emailEnabledCapsWorkPerTransaction() {
        runner.withPropertyValues("payflow.auth-email.enabled=true",
                "payflow.auth-email.api-key=re_unit_test_not_a_real_key", "payflow.auth-email.from=noreply@example.test",
                "payflow.auth-email.frontend-url=https://payflow.example.test")
                .run(context -> assertThat(context.getBean(OutboxProperties.class).getBatchSize()).isEqualTo(1));
    }
}

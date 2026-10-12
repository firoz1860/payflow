package com.payflow.auth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.common.outbox.OutboxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthEmailProperties.class)
@ConditionalOnProperty(prefix = "payflow.auth-email", name = "enabled", havingValue = "true")
public class AuthEmailConfiguration {
    @Bean
    public ResendEmailDelivery authenticationEmailDelivery(AuthEmailProperties properties,
                                                         OneTimeTokenRepository tokens,
                                                         UserRepository users, ObjectMapper mapper,
                                                         OutboxProperties outbox) {
        // Keep the bounded HTTP request inside a single-event transaction.
        // With the default one-second poll, a single instance sends at most one email per second.
        outbox.setBatchSize(1);
        return new ResendEmailDelivery(properties, tokens, users, mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                        .followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("https://api.resend.com/emails"));
    }
}

package com.payflow.auth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.domain.User;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "JWT_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "PAYFLOW_INTERNAL_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
        "payflow.outbox.enabled=false", "payflow.auth-email.enabled=false",
        "PAYFLOW_ADMIN_EMAIL=", "PAYFLOW_ADMIN_PASSWORD=",
        "spring.datasource.hikari.maximum-pool-size=3", "spring.datasource.hikari.minimum-idle=0"
})
@AutoConfigureMockMvc
class AuthEmailDeliveryIT {
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static { postgres.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String baseUrl = postgres.getJdbcUrl();
        properties.add("spring.datasource.url", () -> baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=auth");
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.flyway.schemas", () -> "auth");
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> "auth");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired OneTimeTokenRepository tokens;
    @Autowired OutboxEventRepository events;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @Test void resetRequestRetriesThePersistedEmailAndTheDeliveredLinkResetsThePassword() throws Exception {
        User owner = new User("owner@example.test", "old-password-hash", "Owner", UUID.randomUUID());
        owner.verifyEmail();
        users.saveAndFlush(owner);
        String publicResponse = mvc.perform(post("/api/v1/auth/password-reset/initiate").contentType("application/json")
                        .content("{\"email\":\"owner@example.test\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String unknownResponse = mvc.perform(post("/api/v1/auth/password-reset/initiate").contentType("application/json")
                        .content("{\"email\":\"unknown@example.test\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(publicResponse).isEqualTo(unknownResponse).doesNotContain("token", "reset-password/");
        OutboxEvent event = events.findAll().stream().filter(e -> Topics.NOTIFICATION_REQUESTED.equals(e.getEventType())).findFirst().orElseThrow();
        var requests = new java.util.concurrent.LinkedBlockingQueue<String>();
        var keys = new java.util.concurrent.LinkedBlockingQueue<String>();
        var responseStatus = new java.util.concurrent.atomic.AtomicInteger(503);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] response = "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var properties = new AuthEmailProperties();
            properties.setApiKey("re_unit_test_not_a_real_key");
            properties.setFrom("noreply@example.test");
            properties.setFrontendUrl("https://payflow.example.test");
            var delivery = new ResendEmailDelivery(properties, tokens, users, mapper,
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));
            KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
            when(kafka.send(any(org.apache.kafka.clients.producer.ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
            var publisher = new OutboxPublisher(events, kafka, new OutboxProperties(), new SimpleMeterRegistry(), List.of(delivery));
            var transactions = new TransactionTemplate(transactionManager);
            transactions.executeWithoutResult(ignored -> publisher.publishPending());
            OutboxEvent pending = events.findById(event.getId()).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
            assertThat(pending.getAttempts()).isEqualTo(1);
            String first = requests.remove();
            String key = keys.remove();
            assertThat(pending.getNextAttemptAt()).isAfter(java.time.Instant.now());
            // A deferred email does not consume another attempt or block ready audit events.
            var audit = events.saveAndFlush(new OutboxEvent("User", owner.getId().toString(), Topics.AUDIT_EVENT,
                    owner.getId().toString(), "{}", null));
            transactions.executeWithoutResult(ignored -> publisher.publishPending());
            assertThat(requests).isEmpty();
            assertThat(events.findById(event.getId()).orElseThrow().getAttempts()).isEqualTo(1);
            assertThat(events.findById(audit.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
            responseStatus.set(200);
            // Advance persisted eligibility without waiting in the integration test.
            jdbc.update("UPDATE outbox_events SET next_attempt_at=NOW()-INTERVAL '1 second' WHERE id=?", event.getId());
            transactions.executeWithoutResult(ignored -> publisher.publishPending());
            assertThat(events.findById(event.getId()).orElseThrow().getStatus()).isEqualTo(OutboxEvent.Status.PUBLISHED);
            assertThat(requests.remove()).isEqualTo(first);
            assertThat(keys.remove()).isEqualTo(key).isEqualTo("auth-email/" + event.getId());
            String text = mapper.readTree(first).path("text").asText();
            String rawToken = text.split("/reset-password/")[1].split("\\s")[0];
            String complete = mapper.writeValueAsString(Map.of("token", rawToken, "newPassword", "ChangedPassword2026!"));
            mvc.perform(post("/api/v1/auth/password-reset/complete").contentType("application/json").content(complete))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/auth/password-reset/complete").contentType("application/json").content(complete))
                    .andExpect(status().isBadRequest());
        } finally { server.stop(0); }
    }
}

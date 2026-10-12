package com.payflow.auth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.domain.OneTimeToken;
import com.payflow.auth.domain.User;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxEvent;
import com.payflow.common.crypto.Hashing;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResendEmailDeliveryTest {
    private static final String RAW_TOKEN = "unit_test_token_that_is_long_enough_123456789";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final OneTimeTokenRepository tokens = mock(OneTimeTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final User user = new User("owner@example.test", "unused", "Owner", UUID.randomUUID());
    private final LinkedBlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private HttpServer server;
    private int status = 200;
    private String response = "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}";
    private ResendEmailDelivery delivery;

    record Request(String authorization, String idempotencyKey, String body) {}

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            requests.add(new Request(exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                    new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        delivery = new ResendEmailDelivery(properties(), tokens, users, mapper,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"));
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test void sendsAResetLinkToTheStoredUserThroughTheEmailApi() throws Exception {
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        OutboxEvent event = event("PASSWORD_RESET", "owner@example.test");
        delivery.publish(event);
        Request request = requests.remove();
        var body = mapper.readTree(request.body());
        assertThat(request.authorization()).isEqualTo("Bearer re_unit_test_not_a_real_key");
        assertThat(request.idempotencyKey()).isEqualTo("auth-email/" + event.getId());
        assertThat(body.path("from").asText()).isEqualTo("Payflow <noreply@example.test>");
        assertThat(body.path("to").get(0).asText()).isEqualTo(user.getEmail());
        assertThat(body.path("subject").asText()).isEqualTo("Reset your Payflow password");
        assertThat(body.path("text").asText()).contains("https://payflow.example.test/reset-password/" + RAW_TOKEN);
        assertThat(body.has("html")).isFalse();
    }

    @Test void sendsTheVerificationPageLink() throws Exception {
        validToken(OneTimeToken.Purpose.EMAIL_VERIFICATION);
        delivery.publish(event("EMAIL_VERIFICATION", user.getEmail()));
        var body = mapper.readTree(requests.remove().body());
        assertThat(body.path("subject").asText()).isEqualTo("Verify your Payflow email");
        assertThat(body.path("text").asText()).contains("https://payflow.example.test/verify-email/" + RAW_TOKEN);
    }

    @Test void passwordChangeNoticeDoesNotContainAResetCredential() throws Exception {
        delivery.publish(event("PASSWORD_CHANGED", user.getEmail()));
        var body = mapper.readTree(requests.remove().body());
        assertThat(body.path("subject").asText()).isEqualTo("Your Payflow password changed");
        assertThat(body.path("text").asText()).doesNotContain(RAW_TOKEN).contains("https://payflow.example.test/login");
        verifyNoInteractions(tokens);
    }

    @Test void retriesWithTheSamePayloadAndIdempotencyKeyWithoutLeakingProviderErrors() throws Exception {
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        OutboxEvent event = event("PASSWORD_RESET", user.getEmail());
        status = 503;
        response = "provider error with " + RAW_TOKEN + " re_unit_test_not_a_real_key";
        assertThatThrownBy(() -> delivery.publish(event)).hasMessage("Email provider rejected request (HTTP 503)")
                .hasNoCause();
        Request first = requests.remove();
        status = 200;
        response = "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}";
        delivery.publish(event);
        assertThat(requests.remove()).isEqualTo(first);
    }

    @Test void rejectsAnUnconfirmedSuccessResponse() throws Exception {
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        response = "{}";
        assertThatThrownBy(() -> delivery.publish(event("PASSWORD_RESET", user.getEmail())))
                .hasMessage("Email provider returned an invalid acknowledgement").hasNoCause();
    }

    @Test void doesNotFollowRedirectsWithCredentials() throws Exception {
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        status = 302;
        assertThatThrownBy(() -> delivery.publish(event("PASSWORD_RESET", user.getEmail())))
                .hasMessage("Email provider rejected request (HTTP 302)");
        assertThat(requests).hasSize(1);
    }

    @Test void suppressesExpiredOrConsumedResetTokens() throws Exception {
        OneTimeToken expired = new OneTimeToken(Hashing.sha256Hex(RAW_TOKEN), user.getId(),
                OneTimeToken.Purpose.PASSWORD_RESET, Instant.now().minusSeconds(1));
        when(tokens.findByTokenHashAndPurpose(anyString(), eq(OneTimeToken.Purpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(expired));
        delivery.publish(event("PASSWORD_RESET", user.getEmail()));
        OneTimeToken consumed = validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        consumed.consume();
        delivery.publish(event("PASSWORD_RESET", user.getEmail()));
        assertThat(requests).isEmpty();
    }

    @Test void suppressesTokensOwnedByAnotherUserAndChangedRecipients() throws Exception {
        when(tokens.findByTokenHashAndPurpose(anyString(), eq(OneTimeToken.Purpose.PASSWORD_RESET)))
                .thenReturn(Optional.of(new OneTimeToken("unused", UUID.randomUUID(),
                        OneTimeToken.Purpose.PASSWORD_RESET, Instant.now().plusSeconds(3600))));
        delivery.publish(event("PASSWORD_RESET", user.getEmail()));
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        delivery.publish(event("PASSWORD_RESET", "other@example.test"));
        assertThat(requests).isEmpty();
    }

    @Test void syntheticGuestAddressesNeverReceiveEmail() throws Exception {
        delivery.publish(event("PASSWORD_RESET", "guest-123@payflow.demo"));
        assertThat(requests).isEmpty();
        verifyNoInteractions(tokens);
    }

    @Test void unsupportedTemplatesFailWithoutSending() throws Exception {
        assertThatThrownBy(() -> delivery.publish(event("UNKNOWN", user.getEmail())))
                .hasMessage("Unsupported authentication email template");
        assertThat(requests).isEmpty();
    }

    @Test void legacyEventsAreNotReplayedBeyondTheProviderIdempotencyWindow() throws Exception {
        validToken(OneTimeToken.Purpose.PASSWORD_RESET);
        OutboxEvent event = event("PASSWORD_RESET", user.getEmail());
        org.springframework.test.util.ReflectionTestUtils.setField(event, "createdAt", Instant.now().minusSeconds(86400));
        delivery.publish(event);
        assertThat(requests).isEmpty();
    }

    @Test void malformedPayloadErrorsContainNoResetCredential() {
        var event = new OutboxEvent("User", user.getId().toString(), Topics.NOTIFICATION_REQUESTED,
                "test", "invalid payload " + RAW_TOKEN, null);
        assertThatThrownBy(() -> delivery.publish(event)).hasMessage("Unable to deliver authentication email").hasNoCause();
        assertThat(requests).isEmpty();
    }

    @Test void configurationRejectsUnsafeOrMissingFrontendAndProviderSettings() {
        AuthEmailProperties properties = properties();
        properties.setApiKey("");
        assertThatThrownBy(properties::validate).hasMessage("Authentication email requires a provider key and sender");
        properties = properties();
        properties.setFrontendUrl("http://payflow.example.test");
        assertThatThrownBy(properties::validate).hasMessage("Authentication email requires an HTTPS frontend origin");
        properties.setFrontendUrl("https://payflow.example.test/path?token=x");
        assertThatThrownBy(properties::validate).hasMessage("Authentication email requires an HTTPS frontend origin");
    }

    private AuthEmailProperties properties() {
        var properties = new AuthEmailProperties();
        properties.setEnabled(true);
        properties.setApiKey("re_unit_test_not_a_real_key");
        properties.setFrom("Payflow <noreply@example.test>");
        properties.setFrontendUrl("https://payflow.example.test");
        return properties;
    }

    private OneTimeToken validToken(OneTimeToken.Purpose purpose) {
        var token = new OneTimeToken(Hashing.sha256Hex(RAW_TOKEN), user.getId(), purpose, Instant.now().plusSeconds(3600));
        when(tokens.findByTokenHashAndPurpose(Hashing.sha256Hex(RAW_TOKEN), purpose)).thenReturn(Optional.of(token));
        return token;
    }

    private OutboxEvent event(String template, String recipient) throws Exception {
        String payload = mapper.writeValueAsString(Map.of("data", Map.of("template", template,
                "channel", "EMAIL", "recipient", recipient, "variables", Map.of("token", RAW_TOKEN))));
        return new OutboxEvent("User", user.getId().toString(), Topics.NOTIFICATION_REQUESTED,
                user.getId().toString(), payload, null);
    }
}

package com.payflow.auth.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.domain.OneTimeToken;
import com.payflow.auth.repository.OneTimeTokenRepository;
import com.payflow.auth.repository.UserRepository;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxDelivery;
import com.payflow.common.outbox.OutboxEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResendEmailDelivery implements OutboxDelivery {
    // The provider retains idempotency keys for 24 hours. Do not replay mail
    // beyond that window, even if a legacy outbox row is manually requeued.
    private static final Duration MAX_EVENT_AGE = Duration.ofHours(23);
    private final AuthEmailProperties properties;
    private final OneTimeTokenRepository tokens;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI endpoint;
    ResendEmailDelivery(AuthEmailProperties properties, OneTimeTokenRepository tokens, UserRepository users,
                        ObjectMapper mapper, HttpClient client, URI endpoint) {
        properties.validate();
        if (client.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalStateException("Authentication email must not follow redirects");
        }
        this.properties = properties;
        this.tokens = tokens;
        this.users = users;
        this.mapper = mapper;
        this.client = client;
        this.endpoint = endpoint;
    }
    public boolean supports(OutboxEvent event) {
        return Topics.NOTIFICATION_REQUESTED.equals(event.getEventType());
    }
    public void publish(OutboxEvent event) throws InterruptedException {
        try {
            send(event);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (Exception ex) {
            // The shared publisher records and logs exceptions. Never attach a
            // provider body, request, token, recipient or key to that exception.
            throw new EmailDeliveryException("Unable to deliver authentication email");
        }
    }
    private void send(OutboxEvent event) throws Exception {
        if (event.getCreatedAt().isBefore(Instant.now().minus(MAX_EVENT_AGE))) return;
        var data = mapper.readTree(event.getPayload()).path("data");
        String recipient = data.path("recipient").asText("");
        if (recipient.toLowerCase(java.util.Locale.ROOT).endsWith("@payflow.demo")) return;
        if (!"EMAIL".equals(data.path("channel").asText())) {
            throw new EmailDeliveryException("Unsupported authentication notification channel");
        }
        String template = data.path("template").asText();
        if (!List.of("PASSWORD_RESET", "EMAIL_VERIFICATION", "PASSWORD_CHANGED").contains(template)) {
            throw new EmailDeliveryException("Unsupported authentication email template");
        }
        if (!"User".equals(event.getAggregateType())) {
            throw new EmailDeliveryException("Invalid authentication notification");
        }
        UUID userId = UUID.fromString(event.getAggregateId());
        var user = users.findById(userId).orElse(null);
        if (user == null || !user.getEmail().equalsIgnoreCase(recipient)) return;
        String origin = properties.getFrontendUrl().replaceAll("/+$", "");
        String subject;
        String text;
        if ("PASSWORD_CHANGED".equals(template)) {
            subject = "Your Payflow password changed";
            text = "Your Payflow password was changed. If you did not make this change, contact support immediately.\n\n"
                    + "Sign in: " + origin + "/login";
        } else {
            String rawToken = data.path("variables").path("token").asText("");
            if (!rawToken.matches("[A-Za-z0-9_-]{32,128}")) {
                throw new EmailDeliveryException("Invalid authentication notification");
            }
            boolean reset = "PASSWORD_RESET".equals(template);
            var purpose = reset ? OneTimeToken.Purpose.PASSWORD_RESET : OneTimeToken.Purpose.EMAIL_VERIFICATION;
            var token = tokens.findByTokenHashAndPurpose(Hashing.sha256Hex(rawToken), purpose).orElse(null);
            if (token == null || !token.isUsable() || !token.getUserId().equals(userId)) return;
            subject = reset ? "Reset your Payflow password" : "Verify your Payflow email";
            text = (reset ? "Use this link to reset your Payflow password:" : "Use this link to verify your Payflow email:")
                    + "\n\n" + origin + (reset ? "/reset-password/" : "/verify-email/") + rawToken
                    + "\n\nThis link expires at " + token.getExpiresAt() + ". If you did not request this email, you can ignore it.";
        }
        String body = mapper.writeValueAsString(Map.of("from", properties.getFrom(),
                "to", List.of(user.getEmail()), "subject", subject, "text", text));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Idempotency-Key", "auth-email/" + event.getId())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new EmailDeliveryException("Email provider rejected request (HTTP " + response.statusCode() + ")");
        }
        try {
            UUID.fromString(mapper.readTree(response.body()).path("id").asText());
        } catch (Exception ignored) {
            throw new EmailDeliveryException("Email provider returned an invalid acknowledgement");
        }
    }
    private static final class EmailDeliveryException extends IllegalStateException {
        EmailDeliveryException(String message) { super(message); }
    }
}

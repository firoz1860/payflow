package com.payflow.provider.gateway.razorpay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
@Component
@ConditionalOnExpression("!'${payflow.provider.razorpay.key-id:}'.isBlank()")
public class RazorpayPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);
    private static final String BASE_URL = "https://api.razorpay.com/v1";
    private final WebClient client;
    private final ProviderProperties.Razorpay config;
    private final ObjectMapper objectMapper;
    public RazorpayPaymentGateway(ProviderProperties properties, ObjectMapper objectMapper) {
        this.config = properties.getRazorpay();
        this.objectMapper = objectMapper;
        String basic = Base64.getEncoder().encodeToString(
                (config.getKeyId() + ":" + config.getKeySecret()).getBytes(StandardCharsets.UTF_8));
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
        this.client = WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
    @Override
    public String name() {
        return "razorpay";
    }
    @Override
    public GatewayPayment createPayment(CreateGatewayPaymentCommand command) {
        if ("QR".equalsIgnoreCase(command.paymentMethod())) {
            return createQrCode(command);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("amount", toMinorUnits(command.amount(), command.currency()));
        body.put("currency", command.currency());
        body.put("receipt", command.paymentReference());
        body.put("payment_capture", 1);
        Map<String, String> notes = new HashMap<>();
        notes.put("payflow_reference", command.paymentReference());
        notes.put("payflow_merchant", command.merchantId());
        if (command.metadata() != null) {
            command.metadata().forEach((k, v) -> notes.put("meta_" + k, v));
        }
        body.put("notes", notes);
        try {
            JsonNode response = client.post()
                    .uri("/orders")
                    .header("X-Razorpay-Idempotency-Key", command.idempotencyKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            if (response == null) {
                throw providerError("Empty response from Razorpay", null);
            }
            String orderId = response.path("id").asText();
            log.info("Razorpay order {} created for {}", orderId, command.paymentReference());
            return new GatewayPayment(name(), orderId, GatewayStatus.PENDING,
                    command.amount(), command.currency(),
                    config.getCheckoutUrl() + "?order_id=" + orderId,
                    null, null, null, command.paymentMethod(), null, null, null, null);
        } catch (WebClientResponseException ex) {
            JsonNode error = readError(ex.getResponseBodyAsString());
            String code = error.path("error").path("code").asText("provider_error");
            String description = error.path("error").path("description").asText(ex.getMessage());
            log.error("Razorpay rejected order for {}: {} {}",
                    command.paymentReference(), code, description);
            return new GatewayPayment(name(), null, GatewayStatus.FAILED,
                    command.amount(), command.currency(), null, null, null, null,
                    command.paymentMethod(), code, description, null, null);
        }
    }
    /**
     * Creates a single-use, fixed-amount UPI QR via Razorpay's QR Codes API. The customer
     * scans the returned {@code image_url}; on payment Razorpay fires a {@code qr_code.credited}
     * / {@code payment.captured} webhook that flows back through the normal event path.
     */
    private GatewayPayment createQrCode(CreateGatewayPaymentCommand command) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "upi_qr");
        body.put("name", "PayFlow " + command.paymentReference());
        body.put("usage", "single_use");
        body.put("fixed_amount", true);
        body.put("payment_amount", toMinorUnits(command.amount(), command.currency()));
        if (command.description() != null) {
            body.put("description", command.description());
        }
        Map<String, String> notes = new HashMap<>();
        notes.put("payflow_reference", command.paymentReference());
        notes.put("payflow_merchant", command.merchantId());
        if (command.metadata() != null) {
            command.metadata().forEach((k, v) -> notes.put("meta_" + k, v));
        }
        body.put("notes", notes);
        try {
            JsonNode response = client.post()
                    .uri("/payments/qr_codes")
                    .header("X-Razorpay-Idempotency-Key", command.idempotencyKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            if (response == null) {
                throw providerError("Empty QR response from Razorpay", null);
            }
            String qrId = response.path("id").asText();
            String imageUrl = response.path("image_url").asText(null);
            log.info("Razorpay QR {} created for {}", qrId, command.paymentReference());
            return new GatewayPayment(name(), qrId, GatewayStatus.PENDING,
                    command.amount(), command.currency(), null, null, null, null,
                    "QR", null, null, imageUrl, imageUrl);
        } catch (WebClientResponseException ex) {
            JsonNode error = readError(ex.getResponseBodyAsString());
            String code = error.path("error").path("code").asText("provider_error");
            String description = error.path("error").path("description").asText(ex.getMessage());
            log.error("Razorpay rejected QR for {}: {} {}",
                    command.paymentReference(), code, description);
            return new GatewayPayment(name(), null, GatewayStatus.FAILED,
                    command.amount(), command.currency(), null, null, null, null,
                    "QR", code, description, null, null);
        }
    }
    @Override
    public GatewayPayment getPayment(String providerPaymentId) {
        JsonNode response = client.get()
                .uri("/orders/{id}", providerPaymentId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(8));
        if (response == null) {
            throw providerError("Empty response from Razorpay", null);
        }
        return new GatewayPayment(name(), providerPaymentId, mapStatus(response.path("status").asText()),
                fromMinorUnits(response.path("amount").asLong(), response.path("currency").asText()),
                response.path("currency").asText(), null, null, null, null, null, null, null, null, null);
    }
    @Override
    public GatewayRefund createRefund(RefundCommand command) {
        Map<String, Object> body = Map.of(
                "amount", toMinorUnits(command.amount(), command.currency()),
                "speed", "normal",
                "notes", Map.of("payflow_refund", command.refundReference()));
        try {
            JsonNode response = client.post()
                    .uri("/payments/{id}/refund", command.providerPaymentId())
                    .header("X-Razorpay-Idempotency-Key", command.idempotencyKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(10));
            if (response == null) {
                throw providerError("Empty refund response from Razorpay", null);
            }
            return new GatewayRefund(name(), response.path("id").asText(),
                    command.providerPaymentId(),
                    "processed".equalsIgnoreCase(response.path("status").asText())
                            ? "COMPLETED" : "PROCESSING",
                    command.amount(), null, null);
        } catch (WebClientResponseException ex) {
            JsonNode error = readError(ex.getResponseBodyAsString());
            return new GatewayRefund(name(), null, command.providerPaymentId(), "FAILED",
                    command.amount(),
                    error.path("error").path("code").asText("provider_error"),
                    error.path("error").path("description").asText(ex.getMessage()));
        }
    }
    @Override
    public boolean verifyWebhook(String rawPayload, String signature, String timestamp) {
        if (signature == null || config.getWebhookSecret() == null) {
            return false;
        }
        String expected = Hashing.hmacSha256Hex(config.getWebhookSecret(), rawPayload);
        return Hashing.constantTimeEquals(expected, signature);
    }
    @Override
    public GatewayEvent parseEvent(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText();
            JsonNode entity = root.path("payload").path("payment").path("entity");
            JsonNode card = entity.path("card");
            // The provider payment id we stored is the order id for card/UPI flows and the
            // qr_code id for QR flows; a Razorpay payment.* body only carries the order id,
            // while a qr_code.* body carries the qr_code entity id.
            String providerPaymentId = entity.path("order_id").asText(null);
            if (providerPaymentId == null) {
                providerPaymentId = root.path("payload").path("qr_code").path("entity")
                        .path("id").asText(null);
            }
            return new GatewayEvent(name(),
                    razorpayEventId(root, entity, eventType, providerPaymentId),
                    eventType, providerPaymentId, mapEventStatus(eventType),
                    entity.path("token_id").asText(null),
                    card.path("last4").asText(null),
                    card.path("network").asText(null),
                    mapMethod(entity.path("method").asText(null)),
                    entity.path("error_code").asText(null),
                    entity.path("error_description").asText(null));
        } catch (Exception ex) {
            throw providerError("Unparseable Razorpay event", ex);
        }
    }
    /**
     * A per-event deduplication key. A Razorpay webhook body has no top-level event id
     * (the globally unique id travels in the {@code X-Razorpay-Event-Id} header), and the
     * payment entity id is identical across a payment's whole lifecycle. Deduping on the
     * entity id alone would silently drop the capture that follows an authorization. We
     * therefore derive a key that is stable across a redelivery of the *same* event yet
     * distinct across the lifecycle: event type + subject id + emit timestamp.
     */
    private String razorpayEventId(JsonNode root, JsonNode entity, String eventType,
                                   String providerPaymentId) {
        String subject = entity.path("id").asText(null);
        if (subject == null) {
            subject = providerPaymentId;
        }
        return String.join(":",
                eventType == null ? "event" : eventType,
                subject == null ? "" : subject,
                root.path("created_at").asText(""));
    }
    private long toMinorUnits(BigDecimal amount, String currency) {
        int scale = com.payflow.common.money.Money.scaleOf(currency);
        return amount.movePointRight(scale).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }
    private BigDecimal fromMinorUnits(long minor, String currency) {
        int scale = com.payflow.common.money.Money.scaleOf(currency);
        return BigDecimal.valueOf(minor).movePointLeft(scale).setScale(scale, RoundingMode.UNNECESSARY);
    }
    private GatewayStatus mapStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "paid" -> GatewayStatus.CAPTURED;
            case "attempted" -> GatewayStatus.PENDING;
            case "created" -> GatewayStatus.CREATED;
            default -> GatewayStatus.PENDING;
        };
    }
    private GatewayStatus mapEventStatus(String eventType) {
        return switch (eventType == null ? "" : eventType) {
            case "payment.captured", "order.paid" -> GatewayStatus.CAPTURED;
            case "payment.authorized" -> GatewayStatus.AUTHORIZED;
            case "payment.failed" -> GatewayStatus.FAILED;
            default -> GatewayStatus.PENDING;
        };
    }
    private String mapMethod(String method) {
        if (method == null) {
            return null;
        }
        return switch (method.toLowerCase()) {
            case "card" -> "CARD";
            case "upi" -> "UPI";
            case "netbanking" -> "NET_BANKING";
            case "wallet" -> "WALLET";
            default -> null;
        };
    }
    private JsonNode readError(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }
    private PayFlowException providerError(String message, Throwable cause) {
        return new PayFlowException(ErrorCode.PROVIDER_ERROR,
                org.springframework.http.HttpStatus.BAD_GATEWAY, message, cause);
    }
}

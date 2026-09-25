package com.payflow.provider.gateway.sandbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.common.crypto.Hashing;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway;
import com.payflow.provider.gateway.qr.QrCodeRenderer;
import com.payflow.provider.gateway.qr.UpiIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;
@Component
public class SandboxPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(SandboxPaymentGateway.class);
    private static final BigDecimal DECLINE_AT_CREATE = new BigDecimal("0.99");
    private static final String QR_METHOD = "QR";
    private final ProviderProperties.Sandbox config;
    private final ObjectMapper objectMapper;
    private final QrCodeRenderer qrRenderer;
    private final ApplicationEventPublisher events;
    public SandboxPaymentGateway(ProviderProperties properties, ObjectMapper objectMapper,
                                 QrCodeRenderer qrRenderer, ApplicationEventPublisher events) {
        this.config = properties.getSandbox();
        this.objectMapper = objectMapper;
        this.qrRenderer = qrRenderer;
        this.events = events;
    }
    @Override
    public String name() {
        return "sandbox";
    }
    @Override
    public GatewayPayment createPayment(CreateGatewayPaymentCommand command) {
        boolean qr = QR_METHOD.equalsIgnoreCase(command.paymentMethod());
        String providerPaymentId = (qr ? "sbx_qr_" : "sbx_pay_")
                + UUID.randomUUID().toString().replace("-", "");
        // Test hook: any amount whose minor part is .99 is declined at creation,
        // for every method including QR, so integration tests can force a decline.
        if (command.amount().remainder(BigDecimal.ONE).compareTo(DECLINE_AT_CREATE) == 0) {
            log.info("Sandbox declining {} at creation (test hook)", command.paymentReference());
            return new GatewayPayment(name(), providerPaymentId, GatewayStatus.FAILED,
                    command.amount(), command.currency(), null, null, null, null,
                    command.paymentMethod(), "card_declined",
                    "The sandbox declined this payment at creation", null, null);
        }
        if (qr) {
            String intent = UpiIntent.build(config.getUpiVpa(), config.getPayeeName(),
                    command.amount(), command.currency(),
                    command.paymentReference(), command.description());
            String image = qrRenderer.toPngDataUri(intent);
            log.info("Sandbox created QR payment {} for {} {} (vpa {})",
                    providerPaymentId, command.amount(), command.currency(), config.getUpiVpa());
            GatewayPayment payment = new GatewayPayment(name(), providerPaymentId, GatewayStatus.PENDING,
                    command.amount(), command.currency(), null, null, null, null,
                    QR_METHOD, null, null, intent, image);
            publishCreated(command, providerPaymentId);
            return payment;
        }
        log.info("Sandbox created payment {} for {} {}",
                providerPaymentId, command.amount(), command.currency());
        GatewayPayment payment = new GatewayPayment(name(), providerPaymentId, GatewayStatus.PENDING,
                command.amount(), command.currency(),
                null,
                null, null, null, command.paymentMethod(), null, null, null, null);
        publishCreated(command, providerPaymentId);
        return payment;
    }

    private void publishCreated(CreateGatewayPaymentCommand command, String providerPaymentId) {
        events.publishEvent(new SandboxPaymentCreatedEvent(
                providerPaymentId, command.amount(), command.paymentMethod()));
    }
    @Override
    public GatewayPayment getPayment(String providerPaymentId) {
        return new GatewayPayment(name(), providerPaymentId, GatewayStatus.PENDING,
                null, null, null, null, null, null, null, null, null, null, null);
    }
    @Override
    public GatewayRefund createRefund(RefundCommand command) {
        String refundId = "sbx_rfnd_" + UUID.randomUUID().toString().replace("-", "");
        return new GatewayRefund(name(), refundId, command.providerPaymentId(),
                "COMPLETED", command.amount(), null, null);
    }
    @Override
    public boolean verifyWebhook(String rawPayload, String signature, String timestamp) {
        String expected = Hashing.hmacSha256Hex(config.getWebhookSecret(),
                (timestamp == null ? "" : timestamp) + "." + rawPayload);
        return Hashing.constantTimeEquals(expected, signature);
    }
    @Override
    public GatewayEvent parseEvent(String rawPayload) {
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            return new GatewayEvent(name(),
                    text(root, "eventId"),
                    text(root, "type"),
                    text(root, "providerPaymentId"),
                    GatewayStatus.valueOf(text(root, "status")),
                    text(root, "instrumentToken"),
                    text(root, "cardLast4"),
                    text(root, "cardNetwork"),
                    text(root, "paymentMethod"),
                    text(root, "failureCode"),
                    text(root, "failureMessage"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unparseable sandbox event", ex);
        }
    }
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}

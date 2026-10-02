package com.payflow.provider.gateway.razorpay;
import com.fasterxml.jackson.databind.JsonNode;
import com.payflow.common.crypto.Hashing;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway.GatewayStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import java.time.Duration;
import static com.payflow.provider.gateway.razorpay.RazorpayCheckoutDtos.*;
@Service
@ConditionalOnExpression("!'${payflow.provider.razorpay.key-id:}'.isBlank()")
public class RazorpayCheckoutService {
    private final ProviderProperties.Razorpay config;
    private WebClient client;
    public RazorpayCheckoutService(ProviderProperties properties) {
        config = properties.getRazorpay();
        if (config.getKeyId() == null || !config.getKeyId().startsWith("rzp_test_") || config.getKeySecret() == null || config.getKeySecret().isBlank())
            throw new IllegalStateException("Razorpay Checkout requires TEST credentials");
        client = WebClient.builder().baseUrl("https://api.razorpay.com/v1")
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(Duration.ofSeconds(10))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)))
            .defaultHeaders(headers -> headers.setBasicAuth(config.getKeyId(), config.getKeySecret())).build();
    }
    public CheckoutOptions checkout(String orderId) {
        id(orderId, "order_");
        JsonNode order = get("/orders/" + orderId);
        if (!orderId.equals(order.path("id").asText()) || !"INR".equals(order.path("currency").asText()) || order.path("amount").asLong() <= 0)
            throw unavailable();
        if (!java.util.Set.of("created", "attempted").contains(order.path("status").asText()))
            throw PayFlowException.conflict(ErrorCode.CONFLICT, "Provider order cannot start checkout");
        return new CheckoutOptions("razorpay", "TEST", config.getKeyId(), orderId, order.path("amount").asLong(), "INR");
    }
    public VerifiedPayment verify(VerificationRequest request) {
        id(request.orderId(), "order_"); id(request.paymentId(), "pay_");
        String expected = Hashing.hmacSha256Hex(config.getKeySecret(), request.orderId() + "|" + request.paymentId());
        if (request.signature() == null || !request.signature().matches("[a-fA-F0-9]{64}") || !Hashing.constantTimeEquals(expected, request.signature()))
            throw PayFlowException.unauthorized("Checkout signature verification failed");
        VerifiedPayment payment = fetch(request.paymentId());
        if (!request.orderId().equals(payment.orderId())) throw PayFlowException.unauthorized("Provider payment does not belong to this order");
        return payment;
    }
    public VerifiedPayment fetch(String paymentId) {
        id(paymentId, "pay_");
        JsonNode entity = get("/payments/" + paymentId);
        if (!paymentId.equals(entity.path("id").asText())) throw unavailable();
        return entity(entity);
    }
    public VerifiedPayment reconcile(String orderId) {
        id(orderId, "order_");
        JsonNode collection = get("/orders/" + orderId + "/payments");
        VerifiedPayment latest = null;
        for (JsonNode item : collection.path("items")) {
            VerifiedPayment payment = entity(item);
            if (!orderId.equals(payment.orderId())) throw unavailable();
            if (payment.status() == GatewayStatus.CAPTURED) return payment;
            if (latest == null || payment.status() == GatewayStatus.AUTHORIZED) latest = payment;
        }
        return latest;
    }
    private VerifiedPayment entity(JsonNode entity) {
        GatewayStatus status = switch (entity.path("status").asText()) {
            case "captured" -> GatewayStatus.CAPTURED;
            case "authorized" -> GatewayStatus.AUTHORIZED;
            case "failed" -> GatewayStatus.FAILED;
            case "created" -> GatewayStatus.PENDING;
            default -> throw unavailable();
        };
        String order = entity.path("order_id").asText(); String payment = entity.path("id").asText();
        id(order, "order_"); id(payment, "pay_");
        if (!entity.path("amount").canConvertToLong() || entity.path("amount").asLong() <= 0 || !"INR".equals(entity.path("currency").asText())) throw unavailable();
        return new VerifiedPayment(order, payment, entity.path("amount").asLong(), "INR", status);
    }
    private JsonNode get(String path) {
        try {
            JsonNode result = client.get().uri(path).retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(12));
            if (result == null) throw unavailable();
            return result;
        } catch (Exception ex) { throw unavailable(); }
    }
    private void id(String value, String prefix) {
        if (value == null || value.length() > 128 || !value.matches(prefix + "[A-Za-z0-9]+"))
            throw PayFlowException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid provider identifier");
    }
    private PayFlowException unavailable() {
        return new PayFlowException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, "Payment provider is temporarily unavailable; retry verification");
    }
}

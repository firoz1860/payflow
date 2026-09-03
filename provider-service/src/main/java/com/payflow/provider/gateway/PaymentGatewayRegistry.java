package com.payflow.provider.gateway;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Component
public class PaymentGatewayRegistry {
    private final Map<String, PaymentGateway> gateways;
    private final String defaultProvider;
    public PaymentGatewayRegistry(List<PaymentGateway> gateways,
                                  @Value("${payflow.provider.default:sandbox}") String defaultProvider) {
        this.gateways = gateways.stream()
                .collect(Collectors.toMap(PaymentGateway::name, Function.identity()));
        this.defaultProvider = defaultProvider;
        if (!this.gateways.containsKey(defaultProvider)) {
            throw new IllegalStateException("Configured default provider '" + defaultProvider
                    + "' is not available. Registered: " + this.gateways.keySet());
        }
    }
    public PaymentGateway resolve(String provider) {
        String key = (provider == null || provider.isBlank()) ? defaultProvider : provider;
        PaymentGateway gateway = gateways.get(key);
        if (gateway == null) {
            throw PayFlowException.badRequest(ErrorCode.PROVIDER_ERROR, "Unknown provider: " + key);
        }
        return gateway;
    }
    public PaymentGateway selectFor(String environment, String currency, String paymentMethod) {
        if ("TEST".equalsIgnoreCase(environment) && gateways.containsKey("sandbox")) {
            return gateways.get("sandbox");
        }
        return resolve(defaultProvider);
    }
    public java.util.Set<String> available() {
        return gateways.keySet();
    }
}

package com.payflow.auth.client;

import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class MerchantRegistrationClient {
    private static final Logger log = LoggerFactory.getLogger(MerchantRegistrationClient.class);
    private final WebClient client;
    @Value("${payflow.internal.token}")
    private String registrationSecret;
    @Value("${payflow.internal.response-timeout-ms:12000}")
    private long responseTimeoutMs = 12000;

    public MerchantRegistrationClient(ServiceClientFactory factory,
                                      @Value("${payflow.services.merchant-url}") String merchantUrl) {
        this.client = factory.create(merchantUrl);
    }

    public record CreatedMerchant(UUID id, String merchantCode, String businessName, String status) {
    }

    public CreatedMerchant create(String businessName, String email, String phone,
                                  String country, String defaultCurrency, String password) {
        try {
            return client.post()
                    .uri("/internal/merchants")
                    .header("X-Registration-Key", registrationKey(businessName, email, phone, country, defaultCurrency, password))
                    .bodyValue(java.util.Map.of(
                            "businessName", businessName,
                            "email", email,
                            "phone", phone == null ? "" : phone,
                            "country", country,
                            "defaultCurrency", defaultCurrency))
                    .retrieve()
                    .bodyToMono(CreatedMerchant.class)
                    .block(Duration.ofMillis(responseTimeoutMs + 2000));
        } catch (Exception ex) {
            log.error("Failed to create merchant during registration for {}: {}", email, ex.toString());
            throw new PayFlowException(ErrorCode.INTERNAL_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to complete registration. Please try again.");
        }
    }
    String registrationKey(String businessName, String email, String phone,
                           String country, String currency, String password) {
        try {
            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.List.of(businessName, email, phone == null ? "" : phone, country, currency, password));
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    registrationSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to derive registration key", ex);
        }
    }

}

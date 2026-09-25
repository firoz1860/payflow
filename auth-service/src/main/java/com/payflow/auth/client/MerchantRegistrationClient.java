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

    public MerchantRegistrationClient(ServiceClientFactory factory,
                                      @Value("${payflow.services.merchant-url}") String merchantUrl) {
        this.client = factory.create(merchantUrl);
    }

    public record CreatedMerchant(UUID id, String merchantCode, String businessName, String status) {
    }

    public CreatedMerchant create(String businessName, String email, String phone,
                                  String country, String defaultCurrency) {
        try {
            return client.post()
                    .uri("/internal/merchants")
                    .bodyValue(java.util.Map.of(
                            "businessName", businessName,
                            "email", email,
                            "phone", phone == null ? "" : phone,
                            "country", country,
                            "defaultCurrency", defaultCurrency))
                    .retrieve()
                    .bodyToMono(CreatedMerchant.class)
                    .block(Duration.ofSeconds(5));
        } catch (Exception ex) {
            log.error("Failed to create merchant during registration for {}: {}", email, ex.toString());
            throw new PayFlowException(ErrorCode.INTERNAL_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to complete registration. Please try again.");
        }
    }
}

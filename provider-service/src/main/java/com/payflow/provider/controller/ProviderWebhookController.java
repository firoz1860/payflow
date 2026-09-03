package com.payflow.provider.controller;
import com.payflow.provider.service.ProviderWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController
@RequestMapping("/internal/webhooks/providers")
@Hidden
public class ProviderWebhookController {
    private static final Logger log = LoggerFactory.getLogger(ProviderWebhookController.class);
    private final ProviderWebhookService webhookService;
    public ProviderWebhookController(ProviderWebhookService webhookService) {
        this.webhookService = webhookService;
    }
    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> receive(@PathVariable String provider,
                                                       @RequestBody String rawBody,
                                                       HttpServletRequest request) {
        String signature = firstNonNull(
                request.getHeader("X-Razorpay-Signature"),
                request.getHeader("Stripe-Signature"),
                request.getHeader("X-PayFlow-Sandbox-Signature"));
        String timestamp = request.getHeader("X-PayFlow-Timestamp");
        ProviderWebhookService.Outcome outcome =
                webhookService.handle(provider, rawBody, signature, timestamp);
        return ResponseEntity.ok(Map.of("status", outcome.name().toLowerCase()));
    }
    private String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}

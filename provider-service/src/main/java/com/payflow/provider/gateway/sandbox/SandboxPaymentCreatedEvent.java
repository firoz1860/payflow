package com.payflow.provider.gateway.sandbox;

import java.math.BigDecimal;

public record SandboxPaymentCreatedEvent(
        String providerPaymentId,
        BigDecimal amount,
        String paymentMethod
) {
}

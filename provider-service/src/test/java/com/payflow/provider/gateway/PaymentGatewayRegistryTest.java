package com.payflow.provider.gateway;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentGatewayRegistryTest {
    private PaymentGateway named(String name) {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.name()).thenReturn(name);
        return gateway;
    }
    @Test void testRoutesToConfiguredRazorpay() {
        PaymentGateway sandbox = named("sandbox"), razorpay = named("razorpay");
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(sandbox, razorpay), "razorpay");
        assertSame(razorpay, registry.selectFor("TEST", "INR", "CARD"));
    }
    @Test void liveCreationRejected() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(named("sandbox")), "sandbox");
        assertThrows(RuntimeException.class, () -> registry.selectFor("LIVE", "INR", "CARD"));
    }
    @Test void defaultSandboxPreserved() {
        PaymentGateway sandbox = named("sandbox");
        assertSame(sandbox, new PaymentGatewayRegistry(List.of(sandbox), "sandbox").selectFor("TEST", "INR", "UPI"));
    }
}

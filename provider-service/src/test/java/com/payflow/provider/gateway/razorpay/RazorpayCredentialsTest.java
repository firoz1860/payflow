package com.payflow.provider.gateway.razorpay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.provider.config.ProviderProperties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RazorpayCredentialsTest {
    @Test void missingTestSecretFailsStartup() {
        ProviderProperties properties = new ProviderProperties();
        properties.getRazorpay().setKeyId("rzp_test_example");
        assertThrows(IllegalStateException.class, () -> new RazorpayPaymentGateway(properties, new ObjectMapper(), null));
    }
    @Test void liveCredentialsRejectedInTestStage() {
        ProviderProperties properties = new ProviderProperties();
        properties.getRazorpay().setKeyId("rzp_live_example");
        properties.getRazorpay().setKeySecret("test-secret");
        properties.getRazorpay().setWebhookSecret("test-webhook-secret");
        assertThrows(IllegalStateException.class, () -> new RazorpayPaymentGateway(properties, new ObjectMapper(), null));
    }
}

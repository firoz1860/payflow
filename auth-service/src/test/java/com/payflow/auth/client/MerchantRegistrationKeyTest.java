package com.payflow.auth.client;
import com.payflow.common.client.ServiceClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class MerchantRegistrationKeyTest {
    @Test
    void keyIsStableAndBoundToPasswordAndBusiness() {
        var client = new MerchantRegistrationClient(mock(ServiceClientFactory.class), "https://merchant.example");
        ReflectionTestUtils.setField(client, "registrationSecret", "test-internal-token");
        String key = client.registrationKey("QA", "qa@example.com", null, "IN", "INR", "password1");
        assertThat(key).matches("[a-f0-9]{64}");
        assertThat(client.registrationKey("QA", "qa@example.com", null, "IN", "INR", "password1")).isEqualTo(key);
        assertThat(client.registrationKey("QA", "qa@example.com", null, "IN", "INR", "password2")).isNotEqualTo(key);
        assertThat(client.registrationKey("Other", "qa@example.com", null, "IN", "INR", "password1")).isNotEqualTo(key);
    }
}

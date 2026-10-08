package com.payflow.ai.redaction;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = new Redactor();

    @Test
    void redactsJwt() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4ifQ."
                + "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        assertThat(redactor.redact("token=" + jwt)).contains(Redactor.REDACTED).doesNotContain(jwt);
    }

    @Test
    void redactsAnthropicKey() {
        assertThat(redactor.redact("key sk-ant-api03-abcDEF123456")).isEqualTo("key " + Redactor.REDACTED);
    }

    @Test
    void redactsGenericOpenAiKey() {
        assertThat(redactor.redact("sk-abcDEF1234567890")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void redactsXaiKey() {
        assertThat(redactor.redact("xai-abcDEF1234567890")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void redactsGeminiKey() {
        assertThat(redactor.redact("AIzaSyD-ABCdef1234567890xyz")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void redactsBearerToken() {
        assertThat(redactor.redact("Authorization: Bearer abc.def.ghi"))
                .contains(Redactor.REDACTED).doesNotContain("abc.def.ghi");
    }

    @Test
    void redactsPemPrivateKey() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA\nabc123\n-----END RSA PRIVATE KEY-----";
        assertThat(redactor.redact(pem)).isEqualTo(Redactor.REDACTED).doesNotContain("MIIEpAIBAAKCAQEA");
    }

    @Test
    void redactsPayFlowLiveKey() {
        assertThat(redactor.redact("pk_live_abcDEF1234567890")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void redactsCardPan() {
        assertThat(redactor.redact("card 4111111111111111 used")).isEqualTo("card " + Redactor.REDACTED + " used");
        assertThat(redactor.redact("4111 1111 1111 1111")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void redactsSensitiveKeysInJson() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("databaseUrl", "jdbc:postgresql://user:pass@host/db");
        input.put("connectionString", "Server=host;Password=secret;");
        input.put("apiKeySecret", "sk_live_super_secret");
        input.put("authorization", "Bearer xyz");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactor.redactJson(input);

        assertThat(out.get("databaseUrl")).isEqualTo(Redactor.REDACTED);
        assertThat(out.get("connectionString")).isEqualTo(Redactor.REDACTED);
        assertThat(out.get("apiKeySecret")).isEqualTo(Redactor.REDACTED);
        assertThat(out.get("authorization")).isEqualTo(Redactor.REDACTED);
    }

    @Test
    void preservesSafeFinancialFields() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("paymentReference", "pay_abc123");
        input.put("status", "SUCCEEDED");
        input.put("amount", 1999);
        input.put("currency", "INR");
        input.put("providerPaymentId", "razorpay_pay_9876543210");
        input.put("failureCode", "card_declined");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactor.redactJson(input);

        assertThat(out.get("paymentReference")).isEqualTo("pay_abc123");
        assertThat(out.get("status")).isEqualTo("SUCCEEDED");
        assertThat(out.get("amount")).isEqualTo(1999);
        assertThat(out.get("currency")).isEqualTo("INR");
        assertThat(out.get("providerPaymentId")).isEqualTo("razorpay_pay_9876543210");
        assertThat(out.get("failureCode")).isEqualTo("card_declined");
    }

    @Test
    void recursesIntoNestedStructures() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("status", "FAILED");
        inner.put("token", "sk-ant-should-vanish");
        Map<String, Object> input = Map.of("attempts", List.of(inner));

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redactor.redactJson(input);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attempts = (List<Map<String, Object>>) out.get("attempts");

        assertThat(attempts.get(0).get("status")).isEqualTo("FAILED");
        assertThat(attempts.get(0).get("token")).isEqualTo(Redactor.REDACTED);
    }
}

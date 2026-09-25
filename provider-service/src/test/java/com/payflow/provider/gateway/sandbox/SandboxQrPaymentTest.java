package com.payflow.provider.gateway.sandbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.provider.config.ProviderProperties;
import com.payflow.provider.gateway.PaymentGateway;
import com.payflow.provider.gateway.qr.QrCodeRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class SandboxQrPaymentTest {
    private final SandboxPaymentGateway gateway = new SandboxPaymentGateway(
            new ProviderProperties(), new ObjectMapper(), new QrCodeRenderer(), event -> { });
    private PaymentGateway.CreateGatewayPaymentCommand command(BigDecimal amount, String method) {
        return new PaymentGateway.CreateGatewayPaymentCommand(
                "pay_ref_123", "merchant-1", amount, "INR", "Order 42",
                method, null, "TEST", "idem-1", Map.of());
    }
    @Test
    @DisplayName("a QR payment yields a scannable UPI intent and a rendered PNG data URI")
    void qrPaymentProducesUpiIntentAndImage() {
        PaymentGateway.GatewayPayment result =
                gateway.createPayment(command(new BigDecimal("250.00"), "QR"));
        assertThat(result.status()).isEqualTo(PaymentGateway.GatewayStatus.PENDING);
        assertThat(result.providerPaymentId()).startsWith("sbx_qr_");
        assertThat(result.paymentMethod()).isEqualTo("QR");
        assertThat(result.qrCodeData())
                .startsWith("upi://pay?")
                .contains("pa=payflow.sandbox%40upi")
                .contains("am=250.00")
                .contains("cu=INR")
                .contains("tr=pay_ref_123");
        assertThat(result.qrCodeImage()).startsWith("data:image/png;base64,");
        // A real PNG renders to a non-trivial number of base64 characters.
        assertThat(result.qrCodeImage().length()).isGreaterThan(200);
    }
    @Test
    @DisplayName("a non-QR payment carries no QR fields")
    void cardPaymentHasNoQr() {
        PaymentGateway.GatewayPayment result =
                gateway.createPayment(command(new BigDecimal("250.00"), "CARD"));
        assertThat(result.qrCodeData()).isNull();
        assertThat(result.qrCodeImage()).isNull();
        assertThat(result.checkoutUrl()).contains("?payment=");
    }
    @Test
    @DisplayName("the .99 decline hook still fires for QR payments")
    void qrDeclineHookHonoured() {
        PaymentGateway.GatewayPayment result =
                gateway.createPayment(command(new BigDecimal("10.99"), "QR"));
        assertThat(result.status()).isEqualTo(PaymentGateway.GatewayStatus.FAILED);
        assertThat(result.qrCodeData()).isNull();
    }
}

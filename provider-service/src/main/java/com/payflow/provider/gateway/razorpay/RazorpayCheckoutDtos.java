package com.payflow.provider.gateway.razorpay;
import com.payflow.provider.gateway.PaymentGateway.GatewayStatus;
public final class RazorpayCheckoutDtos {
    private RazorpayCheckoutDtos() {}
    public record CheckoutOptions(String provider, String mode, String keyId, String orderId, long amountMinor, String currency) {}
    public record VerificationRequest(String orderId, String paymentId, String signature) {}
    public record VerifiedPayment(String orderId, String paymentId, long amountMinor, String currency, GatewayStatus status) {}
}

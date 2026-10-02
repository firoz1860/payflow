package com.payflow.payment.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public final class CheckoutDtos {
    private CheckoutDtos() {}
    public record Options(String provider, String mode, String keyId, String orderId, long amountMinor, String currency) {}
    public record VerificationRequest(@NotBlank @Size(max=128) String orderId, @NotBlank @Size(max=128) String paymentId, @NotBlank @Pattern(regexp="[a-fA-F0-9]{64}") String signature) {}
    public record VerifiedPayment(String orderId, String paymentId, long amountMinor, String currency, String status) {}
}

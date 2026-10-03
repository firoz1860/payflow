package com.payflow.provider.gateway.razorpay;
public interface RazorpayOrderAttemptStore {
    record Claim(boolean acquired, String responseJson) {}
    Claim claim(String keyHash, String reference, String requestHash);
    void complete(String keyHash, String responseJson);
    void uncertain(String keyHash);
}

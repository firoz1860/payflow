package com.payflow.payment.service;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
@Component
public class ReferenceGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    public String paymentReference() {
        return "pay_" + random(24);
    }
    public String attemptReference() {
        return "att_" + random(24);
    }
    private String random(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}

package com.payflow.payment.domain;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
public enum PaymentStatus {
    CREATED,
    PENDING,
    PROCESSING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED,
    PARTIALLY_REFUNDED,
    REFUNDED;
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            CREATED, EnumSet.of(PENDING, PROCESSING, FAILED, CANCELLED),
            PENDING, EnumSet.of(PROCESSING, AUTHORIZED, CAPTURED, FAILED, CANCELLED),
            PROCESSING, EnumSet.of(AUTHORIZED, CAPTURED, FAILED, CANCELLED),
            AUTHORIZED, EnumSet.of(CAPTURED, FAILED, CANCELLED),
            CAPTURED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            PARTIALLY_REFUNDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            REFUNDED, EnumSet.noneOf(PaymentStatus.class),
            FAILED, EnumSet.noneOf(PaymentStatus.class),
            CANCELLED, EnumSet.noneOf(PaymentStatus.class)
    );
    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == REFUNDED;
    }
    public boolean isRefundable() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }
}

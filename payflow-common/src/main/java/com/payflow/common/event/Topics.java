package com.payflow.common.event;
public final class Topics {
    public static final String PAYMENT_CREATED = "payment.created";
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";
    public static final String PAYMENT_CAPTURED = "payment.captured";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String REFUND_CREATED = "refund.created";
    public static final String REFUND_COMPLETED = "refund.completed";
    public static final String REFUND_FAILED = "refund.failed";
    public static final String SETTLEMENT_CREATED = "settlement.created";
    public static final String SETTLEMENT_COMPLETED = "settlement.completed";
    public static final String SETTLEMENT_FAILED = "settlement.failed";
    public static final String LEDGER_POSTED = "ledger.posted";
    public static final String MERCHANT_WEBHOOK_REQUESTED = "merchant.webhook.requested";
    public static final String NOTIFICATION_REQUESTED = "notification.requested";
    public static final String AUDIT_EVENT = "audit.event";
    public static final String DLT_SUFFIX = ".DLT";
    private Topics() {
    }
}

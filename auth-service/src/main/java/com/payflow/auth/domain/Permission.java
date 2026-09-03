package com.payflow.auth.domain;
public enum Permission {
    PAYMENTS_CREATE("payments:create"),
    PAYMENTS_READ("payments:read"),
    REFUNDS_CREATE("refunds:create"),
    REFUNDS_READ("refunds:read"),
    CUSTOMERS_MANAGE("customers:manage"),
    CUSTOMERS_READ("customers:read"),
    WEBHOOKS_MANAGE("webhooks:manage"),
    SETTLEMENTS_READ("settlements:read"),
    API_KEYS_MANAGE("api_keys:manage"),
    MERCHANT_MANAGE("merchant:manage"),
    MERCHANT_READ("merchant:read"),
    TEAM_MANAGE("team:manage"),
    LEDGER_READ("ledger:read"),
    PLATFORM_ADMIN("platform:admin");
    private final String value;
    Permission(String value) {
        this.value = value;
    }
    public String value() {
        return value;
    }
}

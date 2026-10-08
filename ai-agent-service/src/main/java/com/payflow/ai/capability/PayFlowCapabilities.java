package com.payflow.ai.capability;

import java.util.List;

/**
 * The single source of truth for what PayFlow actually runs vs. what is only
 * planned. Grounded in the repository audit (docs/superpowers/specs/
 * payflow-ai-agent-current-state.md). This backs both the {@code /capabilities}
 * endpoint and the {@code get_service_capability} tool, so the Copilot answers
 * "that service is not implemented" from code — never from an LLM guess.
 */
public final class PayFlowCapabilities {

    public enum Status { IMPLEMENTED, PLANNED }

    public record Capability(String key, String name, Status status, String note) {}

    private static final List<Capability> CAPABILITIES = List.of(
            new Capability("auth", "Auth Service", Status.IMPLEMENTED,
                    "Users, JWT, refresh rotation with reuse detection, permission RBAC."),
            new Capability("merchant", "Merchant Service", Status.IMPLEMENTED,
                    "Merchants, TEST/LIVE API keys, key verification. API-key secret shown once."),
            new Capability("payment", "Payment Service", Status.IMPLEMENTED,
                    "Payment lifecycle, attempts, idempotency, provider orchestration, outbox."),
            new Capability("provider", "Provider Service", Status.IMPLEMENTED,
                    "Gateway abstraction, sandbox + Razorpay, signed webhook verification, event dedup."),
            new Capability("ledger", "Ledger Service", Status.IMPLEMENTED,
                    "Double-entry accounts/postings/entries, reversals, scheduled integrity job."),
            new Capability("gateway", "API Gateway", Status.IMPLEMENTED,
                    "Routing, Redis rate limiting, JWT pre-check, internal-path blocking."),
            new Capability("refund", "Refund Service", Status.PLANNED,
                    "Specified in ROADMAP; not implemented in this deployment."),
            new Capability("settlement", "Settlement Service", Status.PLANNED,
                    "Specified in ROADMAP; not implemented in this deployment."),
            new Capability("reconciliation", "Reconciliation Service", Status.PLANNED,
                    "Specified in ROADMAP; not implemented in this deployment."),
            new Capability("risk", "Risk Service", Status.PLANNED,
                    "Payment service has a client for it, but the service is not implemented."),
            new Capability("webhook", "Merchant Webhook Service", Status.PLANNED,
                    "Outbound merchant webhooks; specified in ROADMAP, not implemented."),
            new Capability("customer", "Customer Service", Status.PLANNED,
                    "Specified in ROADMAP; not implemented in this deployment."),
            new Capability("notification", "Notification Service", Status.PLANNED,
                    "Specified in ROADMAP; not implemented in this deployment."),
            new Capability("audit", "Audit Service", Status.PLANNED,
                    "Specified in ROADMAP (port 8095); not implemented in this deployment.")
    );

    public static List<Capability> all() {
        return CAPABILITIES;
    }

    public static Capability byKey(String key) {
        return CAPABILITIES.stream()
                .filter(c -> c.key().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }

    private PayFlowCapabilities() {}
}

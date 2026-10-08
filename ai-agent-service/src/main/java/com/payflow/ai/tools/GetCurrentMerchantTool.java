package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.client.MerchantEvidenceClient;
import com.payflow.ai.client.dto.MerchantEvidence;
import com.payflow.ai.redaction.Redactor;
import com.payflow.common.security.PayFlowPrincipal;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code get_current_merchant} — the caller's OWN merchant profile (non-secret
 * fields only). The merchant id comes solely from the authenticated principal, so
 * there is no cross-tenant surface. Platform-admin callers have no implicit
 * merchant and get an absent result.
 */
@Component
public class GetCurrentMerchantTool extends AbstractTool {

    public static final String NAME = "get_current_merchant";

    private final MerchantEvidenceClient client;

    public GetCurrentMerchantTool(MerchantEvidenceClient client, ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch the current merchant's profile (business name, status, currency, fee) — no secrets.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        String resourceRef = "merchant:self";
        long start = System.nanoTime();
        PayFlowPrincipal principal = context.principal();
        if (principal == null || principal.merchantId() == null) {
            return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
        }
        resourceRef = "merchant:" + principal.merchantId();
        try {
            Optional<MerchantEvidence> evidence = client.getMerchant(principal.merchantId());
            if (evidence.isEmpty()) {
                return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
            }
            return ToolResult.success(NAME, resourceRef, elapsedMs(start), redacted(evidence.get()));
        } catch (RuntimeException ex) {
            return ToolResult.failure(NAME, resourceRef, elapsedMs(start));
        }
    }
}

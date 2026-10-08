package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.client.ProviderEvidenceClient;
import com.payflow.ai.client.dto.ProviderEvidence;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code get_provider_evidence} — stored gateway events for a provider payment id.
 * Tenancy for this evidence is asserted upstream via the payment it came from (the
 * provider payment id is only known from the caller's own payment), so this tool
 * trusts the server-supplied id and never accepts a merchant id.
 */
@Component
public class GetProviderEvidenceTool extends AbstractTool {

    public static final String NAME = "get_provider_evidence";

    private final ProviderEvidenceClient client;

    public GetProviderEvidenceTool(ProviderEvidenceClient client, ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch provider-side webhook/event evidence (event type, processing status) for a provider payment id.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        String providerPaymentId = context.arg("providerPaymentId");
        String provider = context.arg("provider");
        String resourceRef = "provider:" + providerPaymentId;
        long start = System.nanoTime();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
        }
        try {
            Optional<ProviderEvidence> evidence = client.getProviderEvidence(providerPaymentId, provider);
            if (evidence.isEmpty()) {
                return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
            }
            return ToolResult.success(NAME, resourceRef, elapsedMs(start), redacted(evidence.get()));
        } catch (RuntimeException ex) {
            return ToolResult.failure(NAME, resourceRef, elapsedMs(start));
        }
    }
}

package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.client.LedgerEvidenceClient;
import com.payflow.ai.client.dto.LedgerEvidence;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code get_ledger_evidence} — double-entry postings for a payment reference,
 * scoped to the caller's merchant. Any posting that belongs to another merchant
 * (non-admin caller) causes the whole result to be treated as not found.
 */
@Component
public class GetLedgerEvidenceTool extends AbstractTool {

    public static final String NAME = "get_ledger_evidence";

    private final LedgerEvidenceClient client;

    public GetLedgerEvidenceTool(LedgerEvidenceClient client, ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch double-entry ledger postings and entries for a payment reference owned by the current merchant.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        String reference = context.arg("reference");
        String resourceRef = "ledger:" + reference;
        long start = System.nanoTime();
        if (reference == null || reference.isBlank()) {
            return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
        }
        try {
            Optional<LedgerEvidence> evidence = client.getLedgerEvidence(reference);
            if (evidence.isEmpty()) {
                return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
            }
            LedgerEvidence ledger = evidence.get();
            if (ledger.postings() != null) {
                boolean crossTenant = ledger.postings().stream()
                        .anyMatch(p -> !tenantAllows(context.principal(), p.merchantId()));
                if (crossTenant) {
                    return ToolResult.denied(NAME, resourceRef, elapsedMs(start));
                }
            }
            return ToolResult.success(NAME, resourceRef, elapsedMs(start), redacted(ledger));
        } catch (RuntimeException ex) {
            return ToolResult.failure(NAME, resourceRef, elapsedMs(start));
        }
    }
}

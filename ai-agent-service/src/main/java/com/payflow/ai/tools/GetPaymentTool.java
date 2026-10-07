package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.client.PaymentEvidenceClient;
import com.payflow.ai.client.dto.PaymentEvidence;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@code get_payment} — authoritative payment evidence for a reference, scoped to
 * the caller's merchant. A cross-tenant reference (non-admin) is returned as
 * absent/{@code DENIED} so existence is never disclosed.
 */
@Component
public class GetPaymentTool extends AbstractTool {

    public static final String NAME = "get_payment";

    private final PaymentEvidenceClient client;

    public GetPaymentTool(PaymentEvidenceClient client, ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Fetch authoritative payment evidence (status, amounts, provider ids, attempts) for a payment reference "
                + "owned by the current merchant.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        String reference = context.arg("reference");
        String resourceRef = "payment:" + reference;
        long start = System.nanoTime();
        if (reference == null || reference.isBlank()) {
            return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
        }
        try {
            Optional<PaymentEvidence> evidence = client.getPayment(reference);
            if (evidence.isEmpty()) {
                return ToolResult.absent(NAME, resourceRef, elapsedMs(start));
            }
            PaymentEvidence payment = evidence.get();
            if (!tenantAllows(context.principal(), payment.merchantId())) {
                return ToolResult.denied(NAME, resourceRef, elapsedMs(start));
            }
            return ToolResult.success(NAME, resourceRef, elapsedMs(start), redacted(payment));
        } catch (RuntimeException ex) {
            return ToolResult.failure(NAME, resourceRef, elapsedMs(start));
        }
    }
}

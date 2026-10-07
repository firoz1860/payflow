package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.client.PaymentEvidenceClient;
import com.payflow.ai.client.dto.PaymentEvidence;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code get_payment_attempts} — just the attempt timeline for a payment, scoped to
 * the caller's merchant. Derived from the same authoritative payment evidence.
 */
@Component
public class GetPaymentAttemptsTool extends AbstractTool {

    public static final String NAME = "get_payment_attempts";

    private final PaymentEvidenceClient client;

    public GetPaymentAttemptsTool(PaymentEvidenceClient client, ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
        this.client = client;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "List the payment attempts (provider, method, status, masked card metadata) for a payment reference "
                + "owned by the current merchant.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        String reference = context.arg("reference");
        String resourceRef = "payment-attempts:" + reference;
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
            List<PaymentEvidence.AttemptEvidence> attempts =
                    payment.attempts() == null ? List.of() : payment.attempts();
            Object data = Map.of(
                    "paymentReference", payment.paymentReference(),
                    "attempts", redacted(attempts));
            return ToolResult.success(NAME, resourceRef, elapsedMs(start), data);
        } catch (RuntimeException ex) {
            return ToolResult.failure(NAME, resourceRef, elapsedMs(start));
        }
    }
}

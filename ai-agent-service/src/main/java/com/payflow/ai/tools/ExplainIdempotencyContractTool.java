package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code explain_idempotency_contract} — static, grounded in the real payment-service
 * implementation. Gives the model accurate, non-fabricated facts about PayFlow's
 * idempotency behaviour so it never guesses.
 */
@Component
public class ExplainIdempotencyContractTool extends AbstractTool {

    public static final String NAME = "explain_idempotency_contract";

    public ExplainIdempotencyContractTool(ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Explain PayFlow's payment idempotency contract (Idempotency-Key header, replay semantics, reuse conflict).";
    }

    @Override
    public ToolResult run(ToolContext context) {
        long start = System.nanoTime();
        Object data = Map.of(
                "summary", "PayFlow enforces idempotency on payment creation via a client-supplied Idempotency-Key header.",
                "rules", List.of(
                        "An Idempotency-Key header is REQUIRED on POST /api/v1/payments.",
                        "Replaying the SAME key with the SAME request body returns the original response "
                                + "with header Idempotent-Replay: true (no new payment is created).",
                        "Reusing the SAME key with a DIFFERENT request body is rejected with HTTP 409 "
                                + "and error code IDEMPOTENCY_KEY_REUSED.",
                        "Idempotency is scoped to the authenticated merchant; keys are never shared across merchants."));
        return ToolResult.success(NAME, "idempotency-contract", elapsedMs(start), redactor.redactJson(data));
    }
}

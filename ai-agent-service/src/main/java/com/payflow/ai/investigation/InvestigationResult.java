package com.payflow.ai.investigation;

import com.payflow.ai.tools.ToolResult;

import java.util.List;

/**
 * The deterministic, provider-neutral output of a payment investigation. The LLM
 * receives this structure (redacted) and reasons over it; it never queries a
 * service itself. The three evidence lists are explicitly separated:
 * {@code facts} (verified), {@code inferences} (derived, clearly labelled), and
 * {@code missing} (absent/unavailable evidence, including planned-but-unbuilt
 * services). {@code toolResults} is what was actually executed, for audit + metrics.
 */
public record InvestigationResult(
        String paymentReference,
        boolean found,
        List<Fact> facts,
        List<String> inferences,
        List<String> missing,
        List<String> warnings,
        Confidence confidence,
        List<EvidenceRef> evidenceRefs,
        List<ToolResult> toolResults) {
}

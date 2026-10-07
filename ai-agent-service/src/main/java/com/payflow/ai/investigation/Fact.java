package com.payflow.ai.investigation;

/**
 * A single verified fact drawn from an authoritative service. {@code source} names
 * which service asserted it (e.g. {@code payment-service}). A fact is never an
 * inference — the two are kept in separate lists so the model (and the UI) cannot
 * blur verified truth with interpretation.
 */
public record Fact(String type, String value, String source) {
}

package com.payflow.ai.investigation;

/**
 * A pointer to a concrete piece of evidence that was consulted. {@code verified} is
 * true when the evidence came back from an authoritative service for this tenant.
 */
public record EvidenceRef(String source, String resourceType, String resourceId, boolean verified) {
}

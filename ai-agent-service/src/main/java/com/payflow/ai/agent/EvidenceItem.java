package com.payflow.ai.agent;

/** A single evidence pointer surfaced to the client: what was consulted and whether it was verified. */
public record EvidenceItem(String source, String resourceType, String resourceId, boolean verified) {
}

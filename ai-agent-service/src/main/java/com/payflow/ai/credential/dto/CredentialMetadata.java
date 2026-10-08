package com.payflow.ai.credential.dto;

import java.time.Instant;

/**
 * Safe, non-secret description of a provider's credential status. Never contains
 * the raw key — only a display mask. {@code source} is {@code USER}, {@code SERVER},
 * or {@code null} when nothing is configured.
 */
public record CredentialMetadata(
        String provider,
        boolean configured,
        String source,
        String masked,
        Instant expiresAt) {
}

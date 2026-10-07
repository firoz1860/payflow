package com.payflow.ai.credential.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to store a BYOK credential. The key is accepted once over HTTPS, held
 * in memory only, and never echoed back. {@code baseUrl} is required (and
 * SSRF-validated) only for a custom OpenAI-compatible provider.
 */
public record CredentialRequest(
        @NotBlank String provider,
        @NotBlank String apiKey,
        String baseUrl) {
}

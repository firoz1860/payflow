package com.payflow.ai.llm;

/** A credential paired with where it was resolved from. */
public record ResolvedCredential(LlmCredential credential, CredentialSource source) {
}

package com.payflow.ai.credential;

/**
 * Produces a non-reversible display mask for a secret so the UI can show the user
 * which key is connected without the raw value ever leaving the backend.
 */
public final class SecretMasker {

    private static final String PLACEHOLDER = "***";

    private SecretMasker() {
    }

    /** First 3 and last 4 characters, e.g. {@code sk-...93ab}; {@code ***} for null/short values. */
    public static String mask(String key) {
        if (key == null || key.length() < 8) {
            return PLACEHOLDER;
        }
        return key.substring(0, 3) + "..." + key.substring(key.length() - 4);
    }
}

package com.payflow.ai.redaction;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Deterministic, code-level redaction applied to every tool result before it can
 * reach the LLM. It strips secrets two ways: by sensitive KEY NAME (for structured
 * Maps) and by VALUE PATTERN (for any string). Non-secret financial fields
 * (payment reference, status, amount, currency, provider ids, failure codes) are
 * explicitly preserved so the model still receives usable evidence.
 *
 * <p>This is a security boundary: retrieved data is untrusted and must never carry
 * an API key, token, JWT, private key, connection string, or card number into the
 * model's context.
 */
@Component
public class Redactor {

    public static final String REDACTED = "***REDACTED***";

    /** Case-insensitive substrings of a field name that mark its value as a secret. */
    private static final List<String> SENSITIVE_KEY_SUBSTRINGS = List.of(
            "authorization", "password", "secret", "token", "cookie", "session",
            "apikey", "api_key", "apikeysecret", "keysecret", "webhooksecret",
            "jwt", "refreshtoken", "refresh_token", "databaseurl", "database_url",
            "connectionstring", "connection_string", "privatekey", "private_key",
            "x-internal-token", "x_internal_token");

    /** Field names whose values are known-safe and must NOT be pattern-redacted. */
    private static final List<String> PRESERVED_KEYS = List.of(
            "paymentreference", "merchantorderid", "providerpaymentid", "status",
            "currency", "amount", "provider", "failurecode", "postingid",
            "createdat", "updatedat", "timestamp", "receivedat");

    private static final List<Pattern> VALUE_PATTERNS = List.of(
            // PEM private keys (multiline) — first, so the whole block collapses.
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"),
            // Bearer tokens.
            Pattern.compile("Bearer\\s+\\S+"),
            // JSON Web Tokens.
            Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
            // Provider API keys (word-anchored to avoid matching inside ordinary words like "task-").
            Pattern.compile("\\bsk-ant-\\S+"),
            Pattern.compile("\\bsk-proj-\\S+"),
            Pattern.compile("\\bsk-\\S+"),
            Pattern.compile("\\bxai-\\S+"),
            Pattern.compile("\\bAIza[0-9A-Za-z_-]{10,}"),
            // PayFlow API keys (pk_/sk_ test|live).
            Pattern.compile("[sp]k_(test|live)_\\S+"),
            // Labeled CVV/CVC.
            Pattern.compile("(?i)\\b(?:cvv2?|cvc|cid)\\b[\\s:=]*\\d{3,4}"),
            // Card PANs: 13-19 digits, grouped by space/dash or consecutive.
            Pattern.compile("\\b\\d{4}[ -]\\d{4}[ -]\\d{4}[ -]\\d{1,7}\\b"),
            Pattern.compile("\\b\\d{13,19}\\b"));

    /** Redacts secrets from a free-text string by value pattern. */
    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (Pattern pattern : VALUE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }

    /**
     * Recursively redacts a structured value (Maps/Lists/scalars) that the
     * orchestration layer passes from a tool result before it reaches the model.
     * A value under a sensitive key is dropped entirely; a value under a preserved
     * key is kept verbatim; any other string is pattern-redacted.
     */
    public Object redactJson(Object value) {
        return redactValue(value, null);
    }

    private Object redactValue(Object value, String key) {
        if (key != null && isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String entryKey = String.valueOf(entry.getKey());
                redacted.put(entryKey, redactValue(entry.getValue(), entryKey));
            }
            return redacted;
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());
            for (Object element : list) {
                // List elements inherit no key; sensitivity is decided by the parent key only.
                redacted.add(redactValue(element, null));
            }
            return redacted;
        }
        if (value instanceof String string) {
            if (key != null && isPreservedKey(key)) {
                return string;
            }
            return redact(string);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String marker : SENSITIVE_KEY_SUBSTRINGS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreservedKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return PRESERVED_KEYS.contains(normalized);
    }
}

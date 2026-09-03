package com.payflow.common.web;
import org.slf4j.MDC;
import java.util.UUID;
public final class CorrelationId {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private CorrelationId() {
    }
    public static String get() {
        return MDC.get(MDC_KEY);
    }
    public static void set(String value) {
        MDC.put(MDC_KEY, value);
    }
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
    public static String generate() {
        return "cid_" + UUID.randomUUID().toString().replace("-", "");
    }
    public static String sanitize(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 64) {
            return generate();
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!ok) {
                return generate();
            }
        }
        return candidate;
    }
}

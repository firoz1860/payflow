package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.redaction.Redactor;
import com.payflow.common.security.PayFlowPrincipal;

/**
 * Common machinery for every tool: converting a typed evidence DTO into a redacted,
 * model-safe {@code Map} tree (via {@link Redactor#redactJson(Object)}), and the
 * server-side tenancy check used by the resource tools. No subclass may bypass the
 * {@link Redactor} — redaction is the security boundary between retrieved data and
 * the model.
 */
public abstract class AbstractTool implements AiTool {

    protected final ObjectMapper objectMapper;
    protected final Redactor redactor;

    protected AbstractTool(ObjectMapper objectMapper, Redactor redactor) {
        this.objectMapper = objectMapper;
        this.redactor = redactor;
    }

    @Override
    public String requiredPermission() {
        return "ai:use";
    }

    /** Serialize a DTO (or list) to a generic tree, then redact every value before it can reach the model. */
    protected Object redacted(Object dto) {
        if (dto == null) {
            return null;
        }
        Object asTree = objectMapper.convertValue(dto, Object.class);
        return redactor.redactJson(asTree);
    }

    /**
     * Tenant ownership check. A platform admin ({@code ai:admin}) may cross tenants;
     * otherwise the resource's merchant id must equal the caller's. A mismatch is
     * reported as "not found" by callers — never "belongs to another merchant".
     */
    protected boolean tenantAllows(PayFlowPrincipal principal, String resourceMerchantId) {
        if (principal != null && principal.hasPermission("ai:admin")) {
            return true;
        }
        if (principal == null || principal.merchantId() == null || resourceMerchantId == null) {
            return false;
        }
        return principal.merchantId().toString().equals(resourceMerchantId);
    }

    protected static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}

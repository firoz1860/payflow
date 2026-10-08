package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.capability.PayFlowCapabilities;
import com.payflow.ai.redaction.Redactor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code get_service_capability} — the code-grounded truth about which PayFlow
 * services are IMPLEMENTED vs PLANNED. This keeps the Copilot from fabricating a
 * capability (e.g. settlement) that this deployment does not run. Static, tenant-free.
 */
@Component
public class GetServiceCapabilityTool extends AbstractTool {

    public static final String NAME = "get_service_capability";

    public GetServiceCapabilityTool(ObjectMapper objectMapper, Redactor redactor) {
        super(objectMapper, redactor);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Report whether a PayFlow service is implemented or only planned (optional arg 'key'); "
                + "lists all services when no key is given.";
    }

    @Override
    public ToolResult run(ToolContext context) {
        long start = System.nanoTime();
        String key = context.arg("key");
        Object data;
        String resourceRef;
        if (key != null && !key.isBlank()) {
            PayFlowCapabilities.Capability capability = PayFlowCapabilities.byKey(key);
            resourceRef = "capability:" + key;
            data = capability == null
                    ? Map.of("key", key, "known", false)
                    : toMap(capability);
        } else {
            resourceRef = "capability:all";
            List<Map<String, Object>> all = PayFlowCapabilities.all().stream()
                    .map(GetServiceCapabilityTool::toMap)
                    .toList();
            data = Map.of("services", all);
        }
        // Static, non-secret content; redact defensively for consistency.
        return ToolResult.success(NAME, resourceRef, elapsedMs(start), redactor.redactJson(data));
    }

    private static Map<String, Object> toMap(PayFlowCapabilities.Capability c) {
        return Map.of(
                "key", c.key(),
                "name", c.name(),
                "status", c.status().name(),
                "implemented", c.status() == PayFlowCapabilities.Status.IMPLEMENTED,
                "note", c.note());
    }
}

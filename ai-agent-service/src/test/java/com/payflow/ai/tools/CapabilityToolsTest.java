package com.payflow.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.redaction.Redactor;
import com.payflow.common.security.PayFlowPrincipal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the two static, tenant-free tools: capability map and idempotency contract. */
class CapabilityToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Redactor redactor = new Redactor();

    private ToolContext context(Map<String, String> args) {
        PayFlowPrincipal principal = PayFlowPrincipal.forUser(UUID.randomUUID(), UUID.randomUUID(), Set.of("ai:use"));
        return ToolContext.of(principal, args);
    }

    @Test
    @SuppressWarnings("unchecked")
    void capabilityToolReportsPlannedServiceAsNotImplemented() {
        GetServiceCapabilityTool tool = new GetServiceCapabilityTool(objectMapper, redactor);

        ToolResult result = tool.run(context(Map.of("key", "settlement")));

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertThat(data.get("status")).isEqualTo("PLANNED");
        assertThat(data.get("implemented")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void capabilityToolListsImplementedServices() {
        GetServiceCapabilityTool tool = new GetServiceCapabilityTool(objectMapper, redactor);

        ToolResult result = tool.run(context(Map.of()));

        Map<String, Object> data = (Map<String, Object>) result.data();
        List<Object> services = (List<Object>) data.get("services");
        assertThat(services).isNotEmpty();
        assertThat(services).anyMatch(s -> "payment".equals(((Map<String, Object>) s).get("key"))
                && Boolean.TRUE.equals(((Map<String, Object>) s).get("implemented")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void idempotencyToolExplainsTheRealContract() {
        ExplainIdempotencyContractTool tool = new ExplainIdempotencyContractTool(objectMapper, redactor);

        ToolResult result = tool.run(context(Map.of()));

        assertThat(result.status()).isEqualTo(ToolStatus.SUCCESS);
        Map<String, Object> data = (Map<String, Object>) result.data();
        assertThat((String) data.get("summary")).contains("Idempotency-Key");
        List<Object> rules = (List<Object>) data.get("rules");
        String joined = rules.stream().map(String::valueOf).reduce("", (a, b) -> a + " " + b);
        assertThat(joined).contains("Idempotent-Replay");
        assertThat(joined).contains("IDEMPOTENCY_KEY_REUSED");
    }
}

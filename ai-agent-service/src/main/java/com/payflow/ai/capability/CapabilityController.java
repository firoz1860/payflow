package com.payflow.ai.capability;

import com.payflow.ai.config.AiProperties;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reports what the Copilot can and cannot do. Deliberately available to any
 * authenticated user and it works even when no LLM credential is configured —
 * the honest "which services exist" surface that keeps the agent from
 * fabricating planned-but-unbuilt capabilities.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class CapabilityController {

    private final AiProperties properties;

    public CapabilityController(AiProperties properties) {
        this.properties = properties;
    }

    public record CapabilitiesResponse(
            boolean aiFeatureEnabled,
            String defaultProvider,
            List<PayFlowCapabilities.Capability> services) {}

    @GetMapping("/capabilities")
    @Operation(summary = "List PayFlow service capabilities (implemented vs planned) and AI feature status")
    public CapabilitiesResponse capabilities() {
        return new CapabilitiesResponse(
                properties.isEnabled(),
                properties.getDefaultProvider() == null || properties.getDefaultProvider().isBlank()
                        ? null : properties.getDefaultProvider(),
                PayFlowCapabilities.all());
    }
}

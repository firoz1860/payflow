package com.payflow.ai.tools;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hardcoded, read-only tool allowlist. Spring injects every {@link AiTool}
 * bean; there is no mechanism to register a tool at runtime, and nothing here can
 * issue an arbitrary HTTP/SQL/shell/file call or return a credential. The agent
 * resolves tools BY NAME from this registry — the model never names a URL or a tool
 * that is not here.
 */
@Component
public class ToolRegistry {

    private final Map<String, AiTool> toolsByName;

    public ToolRegistry(List<AiTool> tools) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : tools) {
            map.put(tool.name(), tool);
        }
        this.toolsByName = Map.copyOf(map);
    }

    public AiTool byName(String name) {
        return name == null ? null : toolsByName.get(name);
    }

    public List<AiTool> all() {
        return List.copyOf(toolsByName.values());
    }

    public boolean contains(String name) {
        return name != null && toolsByName.containsKey(name);
    }
}

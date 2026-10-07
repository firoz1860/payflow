package com.payflow.ai.tools;

/**
 * The structured, already-redacted outcome of a tool call. {@code data} is the
 * redacted, model-safe payload (a {@code Map}/{@code List} tree) or {@code null}
 * when nothing was found (or access was denied — which the orchestration layer
 * treats as "not found", never disclosing cross-tenant existence). The record
 * also carries exactly what is persisted to {@code ai_tool_executions}.
 */
public record ToolResult(String toolName, ToolStatus status, String resourceRef, long latencyMs, Object data) {

    public boolean hasData() {
        return data != null;
    }

    public static ToolResult success(String toolName, String resourceRef, long latencyMs, Object data) {
        return new ToolResult(toolName, ToolStatus.SUCCESS, resourceRef, latencyMs, data);
    }

    public static ToolResult absent(String toolName, String resourceRef, long latencyMs) {
        return new ToolResult(toolName, ToolStatus.SUCCESS, resourceRef, latencyMs, null);
    }

    public static ToolResult denied(String toolName, String resourceRef, long latencyMs) {
        return new ToolResult(toolName, ToolStatus.DENIED, resourceRef, latencyMs, null);
    }

    public static ToolResult failure(String toolName, String resourceRef, long latencyMs) {
        return new ToolResult(toolName, ToolStatus.FAILURE, resourceRef, latencyMs, null);
    }
}

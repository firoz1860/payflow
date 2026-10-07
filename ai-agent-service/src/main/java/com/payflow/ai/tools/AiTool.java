package com.payflow.ai.tools;

/**
 * A single entry in the hardcoded, read-only tool allowlist. The model may never
 * invent a tool, choose a URL, or run SQL/shell/file access — it can only trigger
 * one of these, and even then the orchestration layer (not the model) decides when
 * to run them. Every implementation:
 * <ul>
 *   <li>derives tenancy from {@link ToolContext#principal()} and enforces it server-side;</li>
 *   <li>bounds its own latency and response size (via the typed clients);</li>
 *   <li>passes its output through the {@code Redactor} before returning it;</li>
 *   <li>returns a typed {@link ToolResult} that is safe to persist and to show the model.</li>
 * </ul>
 */
public interface AiTool {

    String name();

    String description();

    /** The permission a caller must hold for this tool (e.g. {@code ai:use}). */
    String requiredPermission();

    ToolResult run(ToolContext context);
}

package com.payflow.ai.tools;

/**
 * Outcome of a single tool execution. Mirrors the {@code ai_tool_executions.status}
 * check constraint in {@code V1__ai_core.sql} so the audit record maps 1:1.
 */
public enum ToolStatus {
    SUCCESS,
    FAILURE,
    DENIED,
    TIMEOUT
}

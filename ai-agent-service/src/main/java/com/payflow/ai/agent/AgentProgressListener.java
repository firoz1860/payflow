package com.payflow.ai.agent;

/**
 * Receives real, server-orchestrated progress events as the agent executes tools.
 * Used by the SSE endpoint to stream one {@code tool} event per executed tool before
 * the final answer. This streams genuine stages — never simulated per-character typing.
 */
@FunctionalInterface
public interface AgentProgressListener {

    void onTool(ToolCall toolCall);
}

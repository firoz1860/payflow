package com.payflow.ai.agent;

/** A tool that the server (never the model) executed, and its outcome. */
public record ToolCall(String name, String status) {
}

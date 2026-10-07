package com.payflow.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Micrometer meters for the Copilot. Registering them in one place keeps the metric
 * names stable and documented. Nothing here records a prompt, an answer, a key, or
 * any evidence content — only counts, latency and token totals.
 */
@Component
public class AiMetrics {

    private final Counter requests;
    private final Counter success;
    private final Counter failure;
    private final Counter toolCalls;
    private final Counter toolFailures;
    private final Timer latency;
    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.requests = Counter.builder("payflow.ai.requests")
                .description("Copilot answer requests received").register(registry);
        this.success = Counter.builder("payflow.ai.success")
                .description("Copilot answers produced successfully").register(registry);
        this.failure = Counter.builder("payflow.ai.failure")
                .description("Copilot answer failures").register(registry);
        this.toolCalls = Counter.builder("payflow.ai.tool.calls")
                .description("Read-only tool executions").register(registry);
        this.toolFailures = Counter.builder("payflow.ai.tool.failures")
                .description("Tool executions that failed or timed out").register(registry);
        this.latency = Timer.builder("payflow.ai.latency")
                .description("End-to-end Copilot answer latency").register(registry);
    }

    public void recordRequest() {
        requests.increment();
    }

    public void recordSuccess() {
        success.increment();
    }

    public void recordFailure() {
        failure.increment();
    }

    public void recordLatency(Duration duration) {
        latency.record(duration);
    }

    public void recordToolCalls(long count) {
        if (count > 0) {
            toolCalls.increment(count);
        }
    }

    public void recordToolFailures(long count) {
        if (count > 0) {
            toolFailures.increment(count);
        }
    }

    public void recordInputTokens(Integer tokens) {
        if (tokens != null && tokens > 0) {
            registry.counter("payflow.ai.tokens.input").increment(tokens);
        }
    }

    public void recordOutputTokens(Integer tokens) {
        if (tokens != null && tokens > 0) {
            registry.counter("payflow.ai.tokens.output").increment(tokens);
        }
    }
}

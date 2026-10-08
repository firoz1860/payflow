package com.payflow.ai.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small, BOUNDED executor for SSE streaming work so a burst of stream requests
 * cannot exhaust threads. Bounded pool + bounded queue + abort policy means excess
 * load is rejected (surfaced to the client) rather than silently queued forever.
 */
@Configuration
public class AiAsyncConfig {

    @Bean(name = "aiStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService aiStreamExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 8,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64),
                runnable -> {
                    Thread thread = new Thread(runnable, "ai-stream-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}

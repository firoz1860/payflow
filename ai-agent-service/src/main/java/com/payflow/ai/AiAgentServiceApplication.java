package com.payflow.ai;

import com.payflow.ai.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PayFlow Copilot — the operations and payment-investigation AI agent.
 *
 * <p>Design invariant: the LLM never determines financial truth. It reasons only
 * over verified evidence retrieved through a hardcoded, read-only tool allowlist.
 * The service starts and the rest of PayFlow keeps working even with no LLM
 * credential configured; AI endpoints then return {@code AI_KEY_REQUIRED}.
 */
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
public class AiAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAgentServiceApplication.class, args);
    }
}

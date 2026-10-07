package com.payflow.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All AI configuration. None of it is required for the service to start — the
 * absence of any LLM credential simply yields {@code AI_KEY_REQUIRED} at request
 * time (BYOK). Optional server-owned provider keys are fallbacks only.
 */
@ConfigurationProperties(prefix = "payflow.ai")
public class AiProperties {

    /** Master switch. When false, AI endpoints report the feature disabled. */
    private boolean enabled = true;

    /** Optional default provider (anthropic|openai|gemini|xai) when a user has not chosen one. */
    private String defaultProvider;

    /** Optional default model id used when a request does not specify one. */
    private String defaultModel;

    /** TTL for an in-memory BYOK credential, in minutes. */
    private int byokTtlMinutes = 60;

    /** Hard ceiling on tool calls per agent turn (cost control). */
    private int maxToolCalls = 8;

    /** Max history messages replayed to the model per turn (cost control). */
    private int maxHistoryMessages = 30;

    /** Per-request LLM timeout, seconds. */
    private int requestTimeoutSeconds = 45;

    /** Conversation retention window, days; a scheduled job purges beyond this. */
    private int conversationRetentionDays = 30;

    /** Optional server-owned fallback keys. Never required; never logged. */
    private final ProviderKeys serverKeys = new ProviderKeys();

    public static class ProviderKeys {
        private String anthropic;
        private String openai;
        private String gemini;
        private String xai;

        public String getAnthropic() { return anthropic; }
        public void setAnthropic(String v) { this.anthropic = v; }
        public String getOpenai() { return openai; }
        public void setOpenai(String v) { this.openai = v; }
        public String getGemini() { return gemini; }
        public void setGemini(String v) { this.gemini = v; }
        public String getXai() { return xai; }
        public void setXai(String v) { this.xai = v; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String v) { this.defaultProvider = v; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String v) { this.defaultModel = v; }
    public int getByokTtlMinutes() { return byokTtlMinutes; }
    public void setByokTtlMinutes(int v) { this.byokTtlMinutes = v; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int v) { this.maxToolCalls = v; }
    public int getMaxHistoryMessages() { return maxHistoryMessages; }
    public void setMaxHistoryMessages(int v) { this.maxHistoryMessages = v; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int v) { this.requestTimeoutSeconds = v; }
    public int getConversationRetentionDays() { return conversationRetentionDays; }
    public void setConversationRetentionDays(int v) { this.conversationRetentionDays = v; }
    public ProviderKeys getServerKeys() { return serverKeys; }
}

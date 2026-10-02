package com.payflow.common.outbox;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "payflow.outbox")
public class OutboxProperties {
    private boolean enabled = true;
    private int batchSize = 100;
    private String sharedTopic;
    private java.util.Set<String> directTopics = java.util.Set.of();
    public String getSharedTopic() { return sharedTopic; }
    public void setSharedTopic(String value) { sharedTopic = value; }
    public java.util.Set<String> getDirectTopics() { return directTopics; }
    public void setDirectTopics(java.util.Set<String> value) { directTopics = value; }
    public String topicFor(String eventType) {
        return sharedTopic == null || sharedTopic.isBlank() || directTopics.contains(eventType)
                ? eventType : sharedTopic;
    }
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    public int getBatchSize() {
        return batchSize;
    }
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}

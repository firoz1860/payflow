package com.payflow.ai.retention;

import com.payflow.ai.config.AiProperties;
import com.payflow.ai.conversation.AiConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Daily retention sweep. Deletes conversations (and, via {@code ON DELETE CASCADE},
 * their messages and tool executions) older than
 * {@link AiProperties#getConversationRetentionDays()}. Privacy by default: the
 * platform does not keep Copilot history indefinitely.
 */
@Component
public class ConversationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRetentionJob.class);

    private final AiConversationRepository conversationRepository;
    private final AiProperties properties;

    public ConversationRetentionJob(AiConversationRepository conversationRepository, AiProperties properties) {
        this.conversationRepository = conversationRepository;
        this.properties = properties;
    }

    /** Runs daily at 03:15. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredConversations() {
        int retentionDays = properties.getConversationRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        long deleted = conversationRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Retention: purged {} conversation(s) older than {} days", deleted, retentionDays);
        }
    }
}

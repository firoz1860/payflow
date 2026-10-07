package com.payflow.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdAndStatusNotOrderByUpdatedAtDesc(UUID userId, ConversationStatus status);

    long deleteByCreatedAtBefore(Instant cutoff);
}

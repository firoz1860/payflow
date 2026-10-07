package com.payflow.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {

    Optional<AiFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);
}

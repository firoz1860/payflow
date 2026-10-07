package com.payflow.ai.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiToolExecutionRepository extends JpaRepository<AiToolExecution, UUID> {
}

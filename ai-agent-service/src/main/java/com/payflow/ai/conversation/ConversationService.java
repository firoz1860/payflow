package com.payflow.ai.conversation;

import com.payflow.ai.tools.ToolResult;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns conversation/message/feedback persistence and, crucially, ownership
 * enforcement. Every read or mutation is filtered by the authenticated user id
 * (and, in merchant mode, the merchant id). A cross-user or cross-tenant access is
 * reported as {@code notFound} — never "forbidden" — so the existence of another
 * user's conversation is not disclosed.
 */
@Service
public class ConversationService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiToolExecutionRepository toolExecutionRepository;
    private final AiFeedbackRepository feedbackRepository;

    public ConversationService(AiConversationRepository conversationRepository,
                               AiMessageRepository messageRepository,
                               AiToolExecutionRepository toolExecutionRepository,
                               AiFeedbackRepository feedbackRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.toolExecutionRepository = toolExecutionRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public AiConversation create(PayFlowPrincipal principal, String title) {
        UUID userId = requireUser(principal);
        ConversationMode mode = principal.merchantId() != null
                ? ConversationMode.MERCHANT : ConversationMode.PLATFORM;
        if (mode == ConversationMode.PLATFORM && !principal.hasPermission("ai:admin")) {
            throw PayFlowException.forbidden("A platform conversation requires ai:admin");
        }
        String safeTitle = (title == null || title.isBlank()) ? "New conversation" : title.trim();
        if (safeTitle.length() > 200) {
            safeTitle = safeTitle.substring(0, 200);
        }
        AiConversation conversation = new AiConversation(userId, principal.merchantId(), safeTitle, mode);
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<AiConversation> list(PayFlowPrincipal principal) {
        return conversationRepository.findByUserIdAndStatusNotOrderByUpdatedAtDesc(
                requireUser(principal), ConversationStatus.DELETED);
    }

    @Transactional(readOnly = true)
    public AiConversation requireOwned(PayFlowPrincipal principal, UUID conversationId) {
        UUID userId = requireUser(principal);
        AiConversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> c.getStatus() != ConversationStatus.DELETED)
                .filter(c -> userId.equals(c.getUserId()))
                .filter(c -> tenantMatches(principal, c))
                .orElseThrow(() -> PayFlowException.notFound("Conversation not found"));
        return conversation;
    }

    @Transactional(readOnly = true)
    public List<AiMessage> messages(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /** Oldest-first history, capped to the most recent {@code max} messages. */
    @Transactional(readOnly = true)
    public List<AiMessage> history(UUID conversationId, int max) {
        List<AiMessage> all = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        if (max <= 0 || all.size() <= max) {
            return all;
        }
        return all.subList(all.size() - max, all.size());
    }

    @Transactional
    public void softDelete(PayFlowPrincipal principal, UUID conversationId) {
        AiConversation conversation = requireOwned(principal, conversationId);
        conversation.setStatus(ConversationStatus.DELETED);
        conversationRepository.save(conversation);
    }

    @Transactional
    public AiMessage saveMessage(UUID conversationId, MessageRole role, String content, String metadataJson) {
        return messageRepository.save(new AiMessage(conversationId, role, content, metadataJson));
    }

    @Transactional
    public void saveToolExecutions(UUID conversationId, UUID messageId, List<ToolResult> results) {
        for (ToolResult result : results) {
            int latency = (int) Math.min(Integer.MAX_VALUE, Math.max(0, result.latencyMs()));
            toolExecutionRepository.save(new AiToolExecution(
                    conversationId, messageId, result.toolName(), result.status(), result.resourceRef(), latency));
        }
    }

    @Transactional
    public void touch(AiConversation conversation) {
        conversationRepository.save(conversation);
    }

    @Transactional
    public AiFeedback saveFeedback(PayFlowPrincipal principal, UUID messageId, FeedbackRating rating, String comment) {
        UUID userId = requireUser(principal);
        AiMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> PayFlowException.notFound("Message not found"));
        // Ownership: the message's conversation must belong to the caller.
        requireOwned(principal, message.getConversationId());
        String safeComment = comment == null ? null
                : (comment.length() > 2000 ? comment.substring(0, 2000) : comment);
        Optional<AiFeedback> existing = feedbackRepository.findByMessageIdAndUserId(messageId, userId);
        if (existing.isPresent()) {
            // Unique per (message,user): replace the prior rating.
            feedbackRepository.delete(existing.get());
        }
        return feedbackRepository.save(new AiFeedback(messageId, userId, rating, safeComment));
    }

    private boolean tenantMatches(PayFlowPrincipal principal, AiConversation conversation) {
        if (principal.hasPermission("ai:admin")) {
            return true;
        }
        if (conversation.getMerchantId() == null) {
            // Platform conversation: only reachable by an admin (handled above).
            return principal.merchantId() == null;
        }
        return conversation.getMerchantId().equals(principal.merchantId());
    }

    private static UUID requireUser(PayFlowPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw PayFlowException.unauthorized("Authentication required");
        }
        return principal.userId();
    }
}

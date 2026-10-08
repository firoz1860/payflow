package com.payflow.ai.conversation;

import com.payflow.common.security.PayFlowPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Thumbs up/down feedback on an assistant message. Ownership is enforced: a user can
 * only rate a message in one of their own conversations.
 */
@RestController
@RequestMapping("/api/v1/ai/feedback")
@Tag(name = "AI Feedback")
public class FeedbackController {

    private final ConversationService conversationService;

    public FeedbackController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public record FeedbackRequest(@NotNull UUID messageId,
                                  @NotNull FeedbackRating rating,
                                  @Size(max = 2000) String comment) {}

    public record FeedbackResponse(UUID id, UUID messageId, String rating) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Record thumbs up/down feedback on an assistant message (ownership enforced)")
    public FeedbackResponse submit(@AuthenticationPrincipal PayFlowPrincipal principal,
                                   @Valid @RequestBody FeedbackRequest request) {
        AiFeedback feedback = conversationService.saveFeedback(
                principal, request.messageId(), request.rating(), request.comment());
        return new FeedbackResponse(feedback.getId(), feedback.getMessageId(), feedback.getRating().name());
    }
}

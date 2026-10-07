package com.payflow.ai.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ai.agent.AgentProgressListener;
import com.payflow.ai.agent.AgentService;
import com.payflow.ai.agent.AnswerResponse;
import com.payflow.ai.config.AiProperties;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copilot conversation API. Every method requires {@code ai:use}; ownership is
 * enforced in {@link ConversationService} (cross-user/cross-tenant → not found).
 * The streaming endpoint emits REAL stages (one {@code tool} event per executed
 * tool, then one {@code message} event with the final answer) — never simulated
 * per-character typing.
 */
@RestController
@RequestMapping("/api/v1/ai/conversations")
@Tag(name = "AI Conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final ExecutorService streamExecutor;
    private final long streamTimeoutMs;

    public ConversationController(ConversationService conversationService,
                                  AgentService agentService,
                                  ObjectMapper objectMapper,
                                  AiProperties properties,
                                  @org.springframework.beans.factory.annotation.Qualifier("aiStreamExecutor")
                                  ExecutorService streamExecutor) {
        this.conversationService = conversationService;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
        this.streamExecutor = streamExecutor;
        this.streamTimeoutMs = Math.max(5_000L, (properties.getRequestTimeoutSeconds() + 10L) * 1000L);
    }

    public record CreateConversationRequest(@Size(max = 200) String title) {}

    public record MessageRequest(@NotBlank @Size(max = 8000) String content,
                                 @Size(max = 32) String resourceType,
                                 @Size(max = 128) String resourceReference) {}

    public record ConversationView(UUID id, String title, String mode, String status,
                                   Instant createdAt, Instant updatedAt) {
        static ConversationView of(AiConversation c) {
            return new ConversationView(c.getId(), c.getTitle(), c.getMode().name(), c.getStatus().name(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record MessageView(UUID id, String role, String content, JsonNode metadata, Instant createdAt) {}

    public record ConversationDetail(ConversationView conversation, List<MessageView> messages) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Create a conversation (MERCHANT mode for a merchant user, PLATFORM for an ai:admin)")
    public ConversationView create(@AuthenticationPrincipal PayFlowPrincipal principal,
                                   @Valid @RequestBody(required = false) CreateConversationRequest request) {
        String title = request == null ? null : request.title();
        return ConversationView.of(conversationService.create(principal, title));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "List the caller's conversations")
    public List<ConversationView> list(@AuthenticationPrincipal PayFlowPrincipal principal) {
        return conversationService.list(principal).stream().map(ConversationView::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Get a conversation and its messages (ownership enforced)")
    public ConversationDetail get(@AuthenticationPrincipal PayFlowPrincipal principal, @PathVariable UUID id) {
        AiConversation conversation = conversationService.requireOwned(principal, id);
        List<MessageView> messages = conversationService.messages(id).stream()
                .map(this::toMessageView)
                .toList();
        return new ConversationDetail(ConversationView.of(conversation), messages);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Soft-delete a conversation (ownership enforced)")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal PayFlowPrincipal principal, @PathVariable UUID id) {
        conversationService.softDelete(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Ask the Copilot; returns a deterministic answer with evidence, confidence and tool calls")
    public AnswerResponse ask(@AuthenticationPrincipal PayFlowPrincipal principal,
                              @PathVariable UUID id,
                              @Valid @RequestBody MessageRequest request) {
        return agentService.answer(principal, id, request.content(),
                request.resourceType(), request.resourceReference());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:use')")
    @Operation(summary = "Stream real agent stages via SSE: a 'tool' event per executed tool, then a 'message' event")
    public SseEmitter stream(@AuthenticationPrincipal PayFlowPrincipal principal,
                             @PathVariable UUID id,
                             @RequestParam @NotBlank String content,
                             @RequestParam(required = false) String resourceType,
                             @RequestParam(required = false) String resourceReference) {
        // Fail fast (as JSON) for ownership/validation before the stream opens.
        conversationService.requireOwned(principal, id);

        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onCompletion(() -> cancelled.set(true));

        streamExecutor.execute(() -> runStream(principal, id, content, resourceType, resourceReference,
                emitter, cancelled));
        return emitter;
    }

    private void runStream(PayFlowPrincipal principal, UUID id, String content, String resourceType,
                           String resourceReference, SseEmitter emitter, AtomicBoolean cancelled) {
        try {
            AgentProgressListener listener = toolCall -> {
                if (cancelled.get()) {
                    throw new CancellationException("client disconnected");
                }
                try {
                    emitter.send(SseEmitter.event().name("tool").data(toolCall, MediaType.APPLICATION_JSON));
                } catch (IOException io) {
                    cancelled.set(true);
                    throw new CancellationException("stream closed");
                }
            };
            if (cancelled.get()) {
                return;
            }
            AnswerResponse response = agentService.answer(principal, id, content, resourceType,
                    resourceReference, listener);
            if (!cancelled.get()) {
                emitter.send(SseEmitter.event().name("message").data(response, MediaType.APPLICATION_JSON));
                emitter.complete();
            }
        } catch (CancellationException cancellation) {
            emitter.complete();
        } catch (PayFlowException pfe) {
            sendErrorEvent(emitter, cancelled, pfe.getCode(), pfe.getMessage());
        } catch (Exception ex) {
            log.warn("SSE stream failed: {}", ex.getClass().getSimpleName());
            sendErrorEvent(emitter, cancelled, ErrorCode.INTERNAL_ERROR, "The Copilot stream failed");
        }
    }

    private void sendErrorEvent(SseEmitter emitter, AtomicBoolean cancelled, ErrorCode code, String message) {
        if (!cancelled.get()) {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("code", code.name(), "message", message), MediaType.APPLICATION_JSON));
            } catch (IOException ignored) {
                // client already gone
            }
        }
        emitter.complete();
    }

    private MessageView toMessageView(AiMessage message) {
        JsonNode metadata = null;
        if (message.getMetadata() != null && !message.getMetadata().isBlank()) {
            try {
                metadata = objectMapper.readTree(message.getMetadata());
            } catch (IOException ignored) {
                metadata = null;
            }
        }
        return new MessageView(message.getId(), message.getRole().name(), message.getContent(),
                metadata, message.getCreatedAt());
    }
}

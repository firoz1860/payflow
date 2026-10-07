package com.payflow.ai.conversation;

import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.security.PayFlowPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Proves conversation ownership is enforced with mocked repositories (no Docker/Postgres needed). */
class ConversationServiceTest {

    private AiConversationRepository conversationRepository;
    private ConversationService service;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();
    private final UUID merchantA = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversationRepository = mock(AiConversationRepository.class);
        service = new ConversationService(conversationRepository,
                mock(AiMessageRepository.class),
                mock(AiToolExecutionRepository.class),
                mock(AiFeedbackRepository.class));
    }

    private AiConversation ownedBy(UUID owner, UUID merchant) {
        AiConversation conversation = mock(AiConversation.class);
        when(conversation.getId()).thenReturn(conversationId);
        when(conversation.getUserId()).thenReturn(owner);
        when(conversation.getMerchantId()).thenReturn(merchant);
        when(conversation.getStatus()).thenReturn(ConversationStatus.ACTIVE);
        return conversation;
    }

    @Test
    void userCannotLoadAnotherUsersConversation() {
        AiConversation conversation = ownedBy(userB, merchantA);
        when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
        PayFlowPrincipal principalA = PayFlowPrincipal.forUser(userA, merchantA, Set.of("ai:use"));

        assertThatThrownBy(() -> service.requireOwned(principalA, conversationId))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void ownerCanLoadTheirConversation() {
        AiConversation conversation = ownedBy(userA, merchantA);
        when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
        PayFlowPrincipal principalA = PayFlowPrincipal.forUser(userA, merchantA, Set.of("ai:use"));

        assertThat(service.requireOwned(principalA, conversationId)).isSameAs(conversation);
    }

    @Test
    void missingConversationIsNotFound() {
        when(conversationRepository.findById(any())).thenReturn(Optional.empty());
        PayFlowPrincipal principalA = PayFlowPrincipal.forUser(userA, merchantA, Set.of("ai:use"));

        assertThatThrownBy(() -> service.requireOwned(principalA, conversationId))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void sameUserDifferentMerchantIsNotFound() {
        // Defense in depth: even the same user id under a different merchant context cannot cross tenants.
        AiConversation conversation = ownedBy(userA, merchantA);
        when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
        PayFlowPrincipal otherTenant = PayFlowPrincipal.forUser(userA, UUID.randomUUID(), Set.of("ai:use"));

        assertThatThrownBy(() -> service.requireOwned(otherTenant, conversationId))
                .isInstanceOf(PayFlowException.class)
                .extracting(ex -> ((PayFlowException) ex).getCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}

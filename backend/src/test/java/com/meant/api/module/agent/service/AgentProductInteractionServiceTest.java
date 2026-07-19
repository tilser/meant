package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.AgentConversationStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentProductInteraction;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentProductInteractionRepository;
import com.meant.api.module.agent.service.dto.AgentProductInteractionReference;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentProductInteractionServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000311");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000312");
    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    private AgentConversationRepository conversations;
    private AgentProductInteractionRepository interactions;
    private AgentProductInteractionReferenceService references;
    private AgentProductInteractionService service;

    @BeforeEach
    void setUp() {
        conversations = mock(AgentConversationRepository.class);
        interactions = mock(AgentProductInteractionRepository.class);
        references = mock(AgentProductInteractionReferenceService.class);
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversation.getStatus()).thenReturn(AgentConversationStatus.ACTIVE);
        when(conversations.findOwnedForUpdate(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(interactions.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service = new AgentProductInteractionService(
                conversations,
                interactions,
                references,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void pinCreatesStateForTheAuthenticatedContextIdentity() {
        AgentToolExecutionContext context = context();
        when(references.resolve(context, "product:boot", "offer:42"))
                .thenReturn(new AgentProductInteractionReference("product:boot", "offer:42", "Boot"));
        AgentProductInteraction state = AgentProductInteraction.builder()
                .userId(USER_ID)
                .canonicalProductKey("product:boot")
                .pinned(false)
                .watched(false)
                .createdAt(NOW)
                .updatedAt(NOW)
                .version(0L)
                .build();
        when(interactions.findForUpdate(USER_ID, "product:boot")).thenReturn(Optional.of(state));

        var result = service.pin(context, "product:boot", "offer:42");

        ArgumentCaptor<AgentProductInteraction> saved = ArgumentCaptor.forClass(AgentProductInteraction.class);
        verify(interactions).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq("product:boot"),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        verify(interactions).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getCanonicalProductKey()).isEqualTo("product:boot");
        assertThat(saved.getValue().isPinned()).isTrue();
        assertThat(saved.getValue().getPinnedOfferKey()).isEqualTo("offer:42");
        assertThat(result.offerKey()).isEqualTo("offer:42");
        assertThat(result.label()).isEqualTo("Boot");
        assertThat(result.pinnedAt()).isEqualTo(NOW);
        assertThat(result.watched()).isFalse();
    }

    @Test
    void repeatedPinIsAStableNoOp() {
        AgentToolExecutionContext context = context();
        Instant firstPin = NOW.minusSeconds(300);
        AgentProductInteraction existing = AgentProductInteraction.createPinned(
                USER_ID, "product:boot", "offer:42", firstPin);
        when(references.resolve(context, "product:boot", "offer:42"))
                .thenReturn(new AgentProductInteractionReference("product:boot", "offer:42", "Boot"));
        when(interactions.findForUpdate(USER_ID, "product:boot")).thenReturn(Optional.of(existing));

        var result = service.pin(context, "product:boot", "offer:42");

        verify(interactions).insertIfAbsent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq("product:boot"),
                org.mockito.ArgumentMatchers.eq(NOW)
        );
        verify(interactions, never()).save(existing);
        assertThat(result.pinnedAt()).isEqualTo(firstPin);
        assertThat(result.updatedAt()).isEqualTo(firstPin);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID, CONVERSATION_ID, UUID.randomUUID(), UUID.randomUUID(), "pin the boot");
    }
}

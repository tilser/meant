package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentArtifactReferenceRepository;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.repository.AgentRunRepository;
import com.meant.api.module.agent.service.command.DeleteAgentConversationCommand;
import com.meant.api.module.agent.service.command.CreateAgentConversationCommand;
import com.meant.api.module.merchant.service.MerchantLookupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private final AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
    private final MerchantLookupService merchantLookupService = mock(MerchantLookupService.class);
    private final AgentConversationService service = new AgentConversationService(
            conversationRepository,
            mock(AgentMessageRepository.class),
            mock(AgentArtifactReferenceRepository.class),
            mock(AgentRunRepository.class),
            merchantLookupService,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void validatesAndPersistsTheSelectedActiveMerchantScope() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        when(conversationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create(new CreateAgentConversationCommand(userId, "Trail shoes", merchantId));

        verify(merchantLookupService).activeSearchResult(merchantId);
        assertThat(created.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void permanentlyDeletesAnOwnedConversation() {
        UUID userId = UUID.randomUUID();
        AgentConversation conversation = AgentConversation.create(userId, "Sunglasses", NOW);
        when(conversationRepository.findOwnedForUpdate(conversation.getId(), userId))
                .thenReturn(Optional.of(conversation));

        service.delete(new DeleteAgentConversationCommand(userId, conversation.getId()));

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void rejectsDeletionWhenTheConversationIsNotOwned() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findOwnedForUpdate(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(new DeleteAgentConversationCommand(userId, conversationId)))
                .isInstanceOf(AgentException.class)
                .hasMessage("The agent resource was not found.");

        verify(conversationRepository, never()).delete(any());
    }
}

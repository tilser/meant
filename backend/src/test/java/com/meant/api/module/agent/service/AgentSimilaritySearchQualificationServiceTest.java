package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentSimilaritySearchQualification;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentSimilaritySearchQualificationRepository;
import com.meant.api.module.agent.service.command.BindAgentSimilaritySearchQualificationCommand;
import com.meant.api.module.agent.service.query.GetAgentSimilaritySearchQualificationQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentSimilaritySearchQualificationServiceTest {

    private static final UUID QUALIFICATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CONVERSATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID INVENTORY_ITEM_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void concurrentReplayConvergesOnTheSameExactOwnedAnchorBinding() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentSimilaritySearchQualificationRepository repository =
                mock(AgentSimilaritySearchQualificationRepository.class);
        AgentConversation conversation = AgentConversation.create(USER_ID, "Search", NOW);
        AgentSimilaritySearchQualification binding = binding("canonical:running-shoes");
        when(conversations.findOwnedForUpdate(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(conversation));
        when(repository.insertIfAbsent(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null,
                "canonical:running-shoes",
                INVENTORY_ITEM_ID,
                "Cool Running Shoes",
                "find similar shoes",
                NOW
        )).thenReturn(1, 0);
        when(repository.findByIdForUpdate(QUALIFICATION_ID)).thenReturn(Optional.of(binding));
        AgentSimilaritySearchQualificationService service = service(conversations, repository);
        BindAgentSimilaritySearchQualificationCommand command = command("canonical:running-shoes");

        var first = service.bind(command);
        var replay = service.bind(command);

        assertThat(first).isEqualTo(replay);
        assertThat(replay.canonicalProductKey()).isEqualTo("canonical:running-shoes");
        assertThat(replay.inventoryItemId()).isEqualTo(INVENTORY_ITEM_ID);
        verify(repository, times(2)).insertIfAbsent(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null,
                "canonical:running-shoes",
                INVENTORY_ITEM_ID,
                "Cool Running Shoes",
                "find similar shoes",
                NOW
        );
    }

    @Test
    void rejectsRebindingAQualificationToFreeTextOrADifferentCanonicalAnchor() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentSimilaritySearchQualificationRepository repository =
                mock(AgentSimilaritySearchQualificationRepository.class);
        when(conversations.findOwnedForUpdate(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(AgentConversation.create(USER_ID, "Search", NOW)));
        when(repository.findByIdForUpdate(QUALIFICATION_ID))
                .thenReturn(Optional.of(binding("canonical:running-shoes")));
        AgentSimilaritySearchQualificationService service = service(conversations, repository);

        assertThatThrownBy(() -> service.bind(command("running shoes mentioned in free text")))
                .isInstanceOf(AgentException.class)
                .satisfies(exception ->
                        assertThat(((AgentException) exception).getStatus().value()).isEqualTo(409));
    }

    @Test
    void readsOnlyThroughTheAuthenticatedConversationOwnerAndMerchantScope() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentSimilaritySearchQualificationRepository repository =
                mock(AgentSimilaritySearchQualificationRepository.class);
        when(conversations.findByIdAndUserId(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(AgentConversation.create(USER_ID, "Search", NOW)));
        when(repository.findById(QUALIFICATION_ID))
                .thenReturn(Optional.of(binding("canonical:running-shoes")));
        AgentSimilaritySearchQualificationService service = service(conversations, repository);

        var found = service.find(new GetAgentSimilaritySearchQualificationQuery(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null
        ));

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().initialUserText()).isEqualTo("find similar shoes");
    }

    @Test
    void rejectsAReservationOutsideTheOwnedMerchantScopeBeforeWritingTheBinding() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentSimilaritySearchQualificationRepository repository =
                mock(AgentSimilaritySearchQualificationRepository.class);
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversations.findOwnedForUpdate(CONVERSATION_ID, USER_ID))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(UUID.randomUUID());
        AgentSimilaritySearchQualificationService service = service(conversations, repository);

        assertThatThrownBy(() -> service.bind(command("canonical:running-shoes")))
                .isInstanceOf(AgentException.class)
                .satisfies(exception ->
                        assertThat(((AgentException) exception).getStatus().value()).isEqualTo(404));
        verifyNoInteractions(repository);
    }

    private AgentSimilaritySearchQualificationService service(
            AgentConversationRepository conversations,
            AgentSimilaritySearchQualificationRepository repository
    ) {
        return new AgentSimilaritySearchQualificationService(
                conversations,
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private BindAgentSimilaritySearchQualificationCommand command(String canonicalProductKey) {
        return new BindAgentSimilaritySearchQualificationCommand(
                QUALIFICATION_ID,
                USER_ID,
                CONVERSATION_ID,
                null,
                canonicalProductKey,
                INVENTORY_ITEM_ID,
                "Cool Running Shoes",
                "find similar shoes"
        );
    }

    private AgentSimilaritySearchQualification binding(String canonicalProductKey) {
        return AgentSimilaritySearchQualification.builder()
                .id(QUALIFICATION_ID)
                .userId(USER_ID)
                .conversationId(CONVERSATION_ID)
                .canonicalProductKey(canonicalProductKey)
                .inventoryItemId(INVENTORY_ITEM_ID)
                .anchorLabel("Cool Running Shoes")
                .initialUserText("find similar shoes")
                .createdAt(NOW)
                .build();
    }
}

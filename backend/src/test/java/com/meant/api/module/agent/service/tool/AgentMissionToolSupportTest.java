package com.meant.api.module.agent.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.constant.ShoppingMissionStatus;
import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.ShoppingMission;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.ShoppingMissionRepository;
import com.meant.api.module.agent.service.AgentJsonSupport;
import com.meant.api.module.agent.service.AgentProductReadReferenceService;
import com.meant.api.module.agent.service.dto.AgentMissionDetails;
import com.meant.api.module.agent.service.dto.AgentMissionToolArguments;
import com.meant.api.module.agent.service.dto.AgentToolExecutionContext;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentMissionToolSupportTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000211");
    private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000212");

    private AgentConversationRepository conversations;
    private ShoppingMissionRepository missions;
    private AgentMissionToolSupport support;
    private AgentConversation conversation;

    @BeforeEach
    void setUp() {
        conversations = mock(AgentConversationRepository.class);
        missions = mock(ShoppingMissionRepository.class);
        conversation = AgentConversation.create(USER_ID, "Picnic", Instant.now());
        when(conversations.findOwnedForUpdate(CONVERSATION_ID, USER_ID)).thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(missions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        support = new AgentMissionToolSupport(
                conversations,
                missions,
                mock(AgentProductReadReferenceService.class),
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                mock(AgentJsonSupport.class)
        );
    }

    @Test
    void selectedExactAlternativeDeterministicallyMakesTheChecklistReady() {
        AgentMissionDetails.Requirement requirement = new AgentMissionDetails.Requirement(
                "blanket", "Picnic blanket", 1, false, List.of("outdoor blanket"));
        AgentMissionDetails.Alternative alternative = new AgentMissionDetails.Alternative(
                "blanket", "product:blanket", "offer:blanket", "Wool blanket", true);

        AgentMissionDetails result = support.create(context(), new AgentMissionToolArguments.Create(
                "Plan a summer picnic",
                List.of(new AgentMissionDetails.Assumption("party size", "2 people")),
                List.of(requirement),
                null,
                List.of(alternative)
        ));

        assertThat(result.status()).isEqualTo(ShoppingMissionStatus.READY);
        assertThat(result.coverage()).singleElement().satisfies(coverage -> {
            assertThat(coverage.requirementId()).isEqualTo("blanket");
            assertThat(coverage.state()).isEqualTo(AgentMissionDetails.CoverageState.COVERED);
            assertThat(coverage.coveredQuantity()).isEqualTo(1);
        });
        assertThat(conversation.getActiveMissionId()).isEqualTo(result.missionId());
        verify(missions).save(any(ShoppingMission.class));
    }

    @Test
    void duplicateChecklistLabelsAreRejectedBeforePersistence() {
        List<AgentMissionDetails.Requirement> requirements = List.of(
                new AgentMissionDetails.Requirement("food", "Snacks", 1, false, List.of()),
                new AgentMissionDetails.Requirement("backup-food", " snacks ", 1, false, List.of())
        );

        assertThatThrownBy(() -> support.create(context(), new AgentMissionToolArguments.Create(
                "Plan a picnic", List.of(), requirements, null, List.of())))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("unique IDs and labels");
    }

    @Test
    void aMissionOwnedByTheUserButBelongingToAnotherConversationIsHidden() {
        UUID missionId = UUID.randomUUID();
        ShoppingMission mission = ShoppingMission.builder()
                .id(missionId)
                .conversationId(UUID.randomUUID())
                .userId(USER_ID)
                .goal("Other conversation")
                .status(ShoppingMissionStatus.ACTIVE)
                .assumptionsJson("[]")
                .requirementsJson("[]")
                .constraintsJson("{}")
                .alternativesJson("[]")
                .coverageJson("[]")
                .cartReferencesJson("[]")
                .checkoutReferencesJson("[]")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(missions.findOwnedForUpdate(missionId, USER_ID)).thenReturn(Optional.of(mission));

        assertThatThrownBy(() -> support.evaluateCoverage(
                context(), new AgentMissionToolArguments.EvaluateCoverage(missionId, List.of())))
                .isInstanceOf(AgentException.class);
    }

    private AgentToolExecutionContext context() {
        return new AgentToolExecutionContext(
                USER_ID,
                CONVERSATION_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test"
        );
    }
}

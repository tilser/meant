package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentProductSearchQualificationServiceTest {

    @Test
    void rejectsConversationOutsideTheAuthenticatedUsersScope() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        UserProductSearchQualificationService qualifications =
                mock(UserProductSearchQualificationService.class);
        AgentProductSearchQualificationService service = service(
                conversations,
                qualifications,
                mock(UserProductSearchQualificationPersistenceService.class),
                mock(UserProductSearchQualificationPlanMapper.class)
        );
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.qualify(command(userId, conversationId, "Find shoes")))
                .isInstanceOf(AgentException.class);
        verifyNoInteractions(qualifications);
    }

    @Test
    void returnsPartialFiltersAndUnsetDimensionsWithoutBlockingSearch() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        UserProductSearchQualificationService qualifications =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistence =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper =
                mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversations.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);

        UserProductSearchQualificationPlan plan =
                mock(UserProductSearchQualificationPlan.class, RETURNS_DEEP_STUBS);
        when(plan.effectiveQuery()).thenReturn("trail shoes");
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SIZE));
        CatalogDiscoveryFilters partialFilters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
        when(mapper.mapAvailable(plan)).thenReturn(partialFilters);
        when(qualifications.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                "Which size?",
                List.of(),
                List.of(),
                "trail shoes"
        ));
        when(persistence.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(new UserProductSearchQualificationSnapshot(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "trail shoes",
                        UserProductSearchQualificationStatus.NEEDS_INPUT,
                        plan,
                        "model",
                        "prompt-v1",
                        Instant.now(),
                        Instant.now()
                )));

        var result = service(conversations, qualifications, persistence, mapper)
                .qualify(command(userId, conversationId, "trail shoes"));

        assertThat(result.authoritativeQuery()).isEqualTo("trail shoes");
        assertThat(result.filters()).isEqualTo(partialFilters);
        assertThat(result.unsetFilters()).containsExactly(UserProductSearchQuestionTarget.SIZE);
        assertThat(result.ready()).isTrue();
    }

    @Test
    void qualifiesADirectActionWithoutTreatingItsRequestIdAsALedgerMessageId() {
        AgentConversationRepository conversations = mock(AgentConversationRepository.class);
        AgentMessageRepository messages = mock(AgentMessageRepository.class);
        UserProductSearchQualificationService qualifications =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistence =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper =
                mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        UUID qualificationId = QualifyUserProductSearchCommand.requestQualificationId(
                userId, conversationId, null, actionId);
        AgentConversation conversation = mock(AgentConversation.class);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationPlan plan =
                mock(UserProductSearchQualificationPlan.class, RETURNS_DEEP_STUBS);
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());

        when(conversations.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);
        when(qualifications.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                null,
                List.of(),
                List.of(),
                "products similar to Predator League"
        ));
        when(persistence.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(new UserProductSearchQualificationSnapshot(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "products similar to Predator League",
                        UserProductSearchQualificationStatus.READY,
                        plan,
                        "model",
                        "prompt-v1",
                        Instant.now(),
                        Instant.now()
                )));
        when(plan.effectiveQuery()).thenReturn("products similar to Predator League");
        when(plan.missingTargets()).thenReturn(List.of());
        when(mapper.mapAvailable(plan)).thenReturn(filters);
        AgentProductSearchQualificationService service = new AgentProductSearchQualificationService(
                conversations,
                messages,
                qualifications,
                persistence,
                mapper,
                mock(AgentProperties.class)
        );

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                null,
                actionId,
                "Found products similar to adidas Predator League",
                "adidas Predator League"
        ));

        assertThat(result.qualificationId()).isEqualTo(qualificationId);
        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualifications).qualify(eq(profile), command.capture());
        assertThat(command.getValue().requestId()).isEqualTo(actionId);
        assertThat(command.getValue().conversation()).isEmpty();
        verifyNoInteractions(messages);
    }

    private AgentProductSearchQualificationService service(
            AgentConversationRepository conversations,
            UserProductSearchQualificationService qualifications,
            UserProductSearchQualificationPersistenceService persistence,
            UserProductSearchQualificationPlanMapper mapper
    ) {
        return new AgentProductSearchQualificationService(
                conversations,
                mock(AgentMessageRepository.class),
                qualifications,
                persistence,
                mapper,
                mock(AgentProperties.class)
        );
    }

    private QualifyAgentProductSearchCommand command(UUID userId, UUID conversationId, String text) {
        return new QualifyAgentProductSearchCommand(
                new EnsureUserProfileCommand(userId, "shopper@example.test", "Shopper", null),
                conversationId,
                null,
                null,
                null,
                text,
                null
        );
    }
}

package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.agent.entity.AgentConversation;
import com.meant.api.module.agent.entity.AgentMessage;
import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.service.query.ResolveAgentPendingProductSearchQuery;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationResult;
import com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserProductSearchCategoryPolicy;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentProductSearchQualificationServiceTest {

    @Test
    void treatsCommonDontCareAnswersAsShippingIndifference() {
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SHIPS_TO));
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        var policy = new AgentProductSearchQualificationContinuationPolicy(
                new UserProductSearchCategoryPolicy());

        assertThat(policy.decide(pending, "I don't care"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "Actually find hiking boots instead"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "Actually hiking boots instead"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "I dont care"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "I don’t care"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);

        when(plan.missingTargets()).thenReturn(List.of(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO
        ));
        assertThat(policy.decide(pending, "XL"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "46"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "46, then add the best pair to my cart"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "I don't care"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "ceramic yarn bowl"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "running shoes"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "Find me a ceramic yarn bowl"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "hiking boots size 46"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        assertThat(policy.decide(pending, "EU shoe size 46"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "shoe size 10"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "my sneaker size is 46"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        assertThat(policy.decide(pending, "tell me a joke"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.PASS_THROUGH);
        assertThat(policy.decide(pending, "what is the return policy?"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.PASS_THROUGH);
    }

    @Test
    void treatsBareMultiwordDestinationsAsAnswersAfterSizeIsResolved() {
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SHIPS_TO));
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        var policy = new AgentProductSearchQualificationContinuationPolicy(
                new UserProductSearchCategoryPolicy());

        for (String destination : List.of("San Francisco", "United States")) {
            assertThat(policy.decide(pending, destination))
                    .as(destination)
                    .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        }
    }

    @Test
    void treatsPlausibleMultiwordValuesAsAnswersToPendingQuestions() {
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        var policy = new AgentProductSearchQualificationContinuationPolicy(
                new UserProductSearchCategoryPolicy());

        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SHIPS_TO));
        for (String destination : List.of(
                "Beverly Hills", "Buenos Aires", "Rio de Janeiro", "Kansas City")) {
            assertThat(policy.decide(pending, destination))
                    .as(destination)
                    .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
        }

        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SIZE));
        assertThat(policy.decide(pending, "extra large"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);

        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.COLOR));
        assertThat(policy.decide(pending, "dark blue"))
                .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.ANSWER);
    }

    @Test
    void recognizesExplicitCompoundProductReplacementRequestsWithoutAFiniteProductVocabulary() {
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SHIPS_TO));
        var policy = new AgentProductSearchQualificationContinuationPolicy(
                new UserProductSearchCategoryPolicy());

        for (List<String> change : List.of(
                List.of("shoe rack", "Actually find a shoe cleaner instead"),
                List.of("phone case", "Show me a phone charger instead"),
                List.of("coffee grinder", "Switch to a coffee maker")
        )) {
            UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null,
                    change.get(0),
                    UserProductSearchQualificationStatus.NEEDS_INPUT,
                    plan,
                    "model",
                    "prompt",
                    Instant.now(),
                    Instant.now()
            );

            assertThat(policy.decide(pending, change.get(1)))
                    .as("%s -> %s", change.get(0), change.get(1))
                    .isEqualTo(AgentProductSearchQualificationContinuationPolicy.Decision.NEW_INTENT);
        }
    }

    @Test
    void rejectsAConversationThatIsNotOwnedByTheAgentUserBeforeQualification() {
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        AgentProductSearchQualificationService service = new AgentProductSearchQualificationService(
                conversationRepository,
                mock(AgentMessageRepository.class),
                qualificationService,
                mock(UserProductSearchQualificationPersistenceService.class),
                mock(UserProductSearchQualificationPlanMapper.class),
                new AgentProductSearchQualificationContinuationPolicy(new UserProductSearchCategoryPolicy()),
                properties(),
                agentProperties()
        );
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserId(conversationId, userId)).thenReturn(Optional.empty());
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);

        assertThatThrownBy(() -> service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, null, null, "Find shoes")))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("agent resource was not found");
        verifyNoInteractions(qualificationService);
    }

    @Test
    void resumesTheExplicitPendingQualificationWhenTheTurnAnswersItsQuestion() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan pendingPlan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot pending = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                pendingPlan
        );
        UserProductSearchQualificationSnapshot ready = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.READY,
                readyPlan
        );
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE, List.of("10"))),
                null,
                List.of()
        );
        when(pendingPlan.missingTargets()).thenReturn(
                List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.SHIPS_TO));
        when(persistenceService.find(any(GetUserProductSearchQualificationQuery.class)))
                .thenReturn(Optional.of(pending), Optional.of(ready));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "football boots"
        ));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("wide football boots");
        when(readyPlan.assistantMessage()).thenReturn("Ready");
        when(mapper.map(readyPlan)).thenReturn(filters);
        var service = service(qualificationService, persistenceService, mapper);

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                qualificationId,
                null,
                "Size 10, shipping to the United States"
        ));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(command.getValue().message()).isEqualTo("Size 10, shipping to the United States");
        assertThat(command.getValue().expectedQualificationUpdatedAt()).isEqualTo(pending.updatedAt());
        assertThat(result.qualificationId()).isEqualTo(qualificationId);
        assertThat(result.ready()).isTrue();
        assertThat(result.filters()).isSameAs(filters);
        assertThat(result.authoritativeQuery()).isEqualTo("wide football boots");
    }

    @Test
    void replaysAnExplicitReadyQualificationAfterCatalogExecutionWasInterrupted() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot ready = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.READY,
                readyPlan
        );
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(ready));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "football boots"
        ));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("football boots");
        when(mapper.map(readyPlan)).thenReturn(filters);
        var service = service(qualificationService, persistenceService, mapper);

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, qualificationId, null, "retry"));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(result.ready()).isTrue();
        assertThat(result.filters()).isSameAs(filters);
    }

    @Test
    void rejectsAnExplicitReadyQualificationForANewUnboundAgentRequest() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UUID distinctRequestId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot ready = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.READY,
                mock(UserProductSearchQualificationPlan.class)
        );
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(ready));
        var service = service(
                qualificationService,
                persistenceService,
                mock(UserProductSearchQualificationPlanMapper.class)
        );

        assertThatThrownBy(() -> service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                qualificationId,
                distinctRequestId,
                "US size 10"
        )))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("changed before this request could be bound");
        verifyNoInteractions(qualificationService);
    }

    @Test
    void rejectsAPreResolvedPendingAnswerWhenTheObservedRevisionHasChanged() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        Instant observedRevision = Instant.parse("2026-07-29T12:00:00Z");
        UserProductSearchQualificationSnapshot advancedPending = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                mock(UserProductSearchQualificationPlan.class),
                "model",
                "prompt",
                observedRevision,
                observedRevision.plusSeconds(1)
        );
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(advancedPending));
        var service = service(
                qualificationService,
                persistenceService,
                mock(UserProductSearchQualificationPlanMapper.class)
        );
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);

        assertThatThrownBy(() -> service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                qualificationId,
                UUID.randomUUID(),
                "US size 10",
                observedRevision
        )))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("changed before this answer could be applied");
        verifyNoInteractions(qualificationService);
    }

    @Test
    void replayingTheOriginalTriggerDoesNotCancelOrRequalifyItsPendingRequest() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        AgentConversation conversation = mock(AgentConversation.class);
        UserProductSearchQualificationPlan pendingPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        QualifyUserProductSearchCommand identity = new QualifyUserProductSearchCommand(
                userId, conversationId, null, "football boots", null, List.of(), requestId);
        UUID qualificationId = identity.requestQualificationId();
        UserProductSearchQualificationSnapshot pending = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                pendingPlan
        );
        AgentMessage triggering = message(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                1,
                "football boots"
        );
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);
        when(messageRepository.findById(requestId)).thenReturn(Optional.of(triggering));
        when(messageRepository.findBuyerVisibleConversationMessages(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.eq(1L),
                any(),
                any()
        )).thenReturn(List.of(triggering));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(pending));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                "What shoe size do you need?",
                List.of(),
                List.of(com.meant.api.module.user.constant.UserProductSearchFilterKind.ATTRIBUTES),
                "football boots"
        ));
        when(pendingPlan.missingFilters()).thenReturn(List.of(
                com.meant.api.module.user.constant.UserProductSearchFilterKind.ATTRIBUTES));
        when(pendingPlan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SIZE));
        when(pendingPlan.effectiveQuery()).thenReturn("football boots");
        when(pendingPlan.assistantMessage()).thenReturn("What shoe size do you need?");
        var service = new AgentProductSearchQualificationService(
                conversationRepository,
                messageRepository,
                qualificationService,
                persistenceService,
                mapper,
                new AgentProductSearchQualificationContinuationPolicy(new UserProductSearchCategoryPolicy()),
                properties(),
                agentProperties()
        );

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, null, requestId, "football boots"));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isNull();
        assertThat(command.getValue().requestId()).isEqualTo(requestId);
        assertThat(result.ready()).isFalse();
        verify(persistenceService, org.mockito.Mockito.never())
                .cancel(any(CancelUserProductSearchQualificationCommand.class));
    }

    @Test
    void replayingAnAnswerTriggerReusesTheReadyQualificationAfterCatalogFailure() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        AgentConversation conversation = mock(AgentConversation.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UUID answerRequestId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot ready = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "cool running shoes",
                UserProductSearchQualificationStatus.READY,
                readyPlan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        AgentMessage answer = message(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                3,
                "46"
        );
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);
        when(messageRepository.findById(answerRequestId)).thenReturn(Optional.of(answer));
        when(messageRepository.findBuyerVisibleConversationMessages(
                org.mockito.ArgumentMatchers.eq(conversationId),
                org.mockito.ArgumentMatchers.eq(3L),
                any(),
                any()
        )).thenReturn(List.of(answer));
        when(persistenceService.findByRequest(
                new FindUserProductSearchQualificationByRequestQuery(
                        userId, conversationId, null, answerRequestId, "46")))
                .thenReturn(Optional.of(ready));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(ready));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "cool running shoes"
        ));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("cool running shoes");
        when(readyPlan.assistantMessage()).thenReturn("Ready");
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of());
        when(mapper.map(readyPlan)).thenReturn(filters);
        var service = new AgentProductSearchQualificationService(
                conversationRepository,
                messageRepository,
                qualificationService,
                persistenceService,
                mapper,
                new AgentProductSearchQualificationContinuationPolicy(new UserProductSearchCategoryPolicy()),
                properties(),
                agentProperties()
        );

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                null,
                answerRequestId,
                "46"
        ));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isNull();
        assertThat(command.getValue().requestId()).isEqualTo(answerRequestId);
        assertThat(result.qualificationId()).isEqualTo(qualificationId);
        assertThat(result.authoritativeQuery()).isEqualTo("cool running shoes");
        assertThat(result.filters()).isSameAs(filters);
        verify(persistenceService, org.mockito.Mockito.never()).findLatestPending(any());
    }

    @Test
    void infersThePendingQualificationWhenTheModelOmitsItsContinuationId() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UserProductSearchQualificationPlan pendingPlan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot pending = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                pendingPlan
        );
        UserProductSearchQualificationSnapshot ready = snapshot(
                qualificationId,
                userId,
                conversationId,
                UserProductSearchQualificationStatus.READY,
                readyPlan
        );
        when(pendingPlan.missingTargets()).thenReturn(
                List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.SHIPS_TO));
        when(persistenceService.findLatestPending(
                new FindPendingUserProductSearchQualificationQuery(userId, conversationId, null)))
                .thenReturn(Optional.of(pending));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(ready));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "running shoes"
        ));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("breathable running shoes");
        when(mapper.map(readyPlan)).thenReturn(new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of()));
        var service = service(qualificationService, persistenceService, mapper);

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, null, null, "46"));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isEqualTo(qualificationId);
        assertThat(command.getValue().message()).isEqualTo("46");
        assertThat(result.qualificationId()).isEqualTo(qualificationId);
        assertThat(result.authoritativeQuery()).isEqualTo("breathable running shoes");
    }

    @Test
    void resolvesANumericPendingAnswerToTheOriginalSearchBeforeTheAgentModelRuns() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SIZE));
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "cool running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.findLatestPending(
                new FindPendingUserProductSearchQualificationQuery(userId, conversationId, null)))
                .thenReturn(Optional.of(pending));
        var service = service(
                qualificationService,
                persistenceService,
                mock(UserProductSearchQualificationPlanMapper.class)
        );

        var continuation = service.resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                userId,
                conversationId,
                null,
                UUID.randomUUID(),
                "46"
        ));

        assertThat(continuation).hasValueSatisfying(value -> {
            assertThat(value.qualificationId()).isEqualTo(qualificationId);
            assertThat(value.originalQuery()).isEqualTo("cool running shoes");
            assertThat(value.observedUpdatedAt()).isEqualTo(pending.updatedAt());
        });
        verifyNoInteractions(qualificationService);
    }

    @Test
    void resolvesBareDestinationAnswersToThePendingSearchBeforeTheAgentModelRuns() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SHIPS_TO));
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "cool running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.findLatestPending(
                new FindPendingUserProductSearchQualificationQuery(userId, conversationId, null)))
                .thenReturn(Optional.of(pending));
        var service = service(
                qualificationService,
                persistenceService,
                mock(UserProductSearchQualificationPlanMapper.class)
        );

        for (String destination : List.of("San Francisco", "United States")) {
            var continuation = service.resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                    userId,
                    conversationId,
                    null,
                    UUID.randomUUID(),
                    destination
            ));

            assertThat(continuation).as(destination).hasValueSatisfying(value ->
                    assertThat(value.qualificationId()).isEqualTo(qualificationId));
        }
        verifyNoInteractions(qualificationService);
    }

    @Test
    void passesAnUnrelatedTurnToTheAgentWithoutCancellingThePendingSearch() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        when(plan.missingTargets()).thenReturn(List.of(UserProductSearchQuestionTarget.SIZE));
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "cool running shoes",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.findLatestPending(
                new FindPendingUserProductSearchQualificationQuery(userId, conversationId, null)))
                .thenReturn(Optional.of(pending));
        var service = service(
                qualificationService,
                persistenceService,
                mock(UserProductSearchQualificationPlanMapper.class)
        );

        var continuation = service.resolvePendingContinuation(new ResolveAgentPendingProductSearchQuery(
                userId,
                conversationId,
                null,
                UUID.randomUUID(),
                "what is the return policy?"
        ));

        assertThat(continuation).isEmpty();
        verify(persistenceService, never()).cancel(any(CancelUserProductSearchQualificationCommand.class));
        verifyNoInteractions(qualificationService);
    }

    @Test
    void sendsEveryBuyerVisibleConversationMessageThroughTheTriggeringTurnToQualification() {
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        AgentMessageRepository messageRepository = mock(AgentMessageRepository.class);
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID triggeringMessageId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);
        AgentMessage firstUser = message(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                1,
                "I usually need delivery in San Francisco."
        );
        AgentMessage assistant = message(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.ASSISTANT,
                2,
                "What are you shopping for?"
        );
        AgentMessage triggering = message(
                conversationId,
                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                3,
                "Cool running shoes"
        );
        when(messageRepository.findById(triggeringMessageId)).thenReturn(Optional.of(triggering));
        when(messageRepository
                .findBuyerVisibleConversationMessages(
                        conversationId,
                        3,
                        List.of(
                                com.meant.api.module.agent.constant.AgentMessageRole.USER,
                                com.meant.api.module.agent.constant.AgentMessageRole.USER_ACTION,
                                com.meant.api.module.agent.constant.AgentMessageRole.ASSISTANT
                        ),
                        org.springframework.data.domain.PageRequest.of(0, 40)
                ))
                .thenReturn(List.of(triggering, assistant, firstUser));
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                qualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "running shoes"
        ));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(new UserProductSearchQualificationSnapshot(
                        qualificationId,
                        userId,
                        conversationId,
                        null,
                        "Cool running shoes",
                        UserProductSearchQualificationStatus.READY,
                        readyPlan,
                        "model",
                        "prompt",
                        Instant.now(),
                        Instant.now()
                )));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("running shoes");
        when(readyPlan.assistantMessage()).thenReturn("Ready");
        when(mapper.map(readyPlan)).thenReturn(new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of()));
        AgentProductSearchQualificationService service = new AgentProductSearchQualificationService(
                conversationRepository,
                messageRepository,
                qualificationService,
                persistenceService,
                mapper,
                new AgentProductSearchQualificationContinuationPolicy(new UserProductSearchCategoryPolicy()),
                properties(),
                agentProperties()
        );
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);

        service.qualify(new QualifyAgentProductSearchCommand(
                profile,
                conversationId,
                null,
                null,
                triggeringMessageId,
                "Cool running shoes"
        ));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().requestId()).isEqualTo(triggeringMessageId);
        assertThat(command.getValue().conversation()).containsExactly(
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.USER,
                        "I usually need delivery in San Francisco."
                ),
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.ASSISTANT,
                        "What are you shopping for?"
                ),
                new UserProductSearchConversationMessage(
                        UserProductSearchConversationMessage.Role.USER,
                        "Cool running shoes"
                )
        );
    }

    @Test
    void startsANewRequestWhenNoPendingQualificationExists() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID newQualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                newQualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "digital course"
        ));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, newQualificationId)))
                .thenReturn(Optional.of(snapshot(
                        newQualificationId, userId, conversationId,
                        UserProductSearchQualificationStatus.READY, readyPlan)));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("digital course");
        when(mapper.map(readyPlan)).thenReturn(new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of()));
        var service = service(qualificationService, persistenceService, mapper);

        service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, null, null, "Find a digital course"));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isNull();
        assertThat(command.getValue().message()).isEqualTo("Find a digital course");
    }

    @Test
    void explicitContinuationTokenStartsFreshWhenTheProductCategoryChanges() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID oldQualificationId = UUID.randomUUID();
        UUID newQualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationPlan pendingPlan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationPlan readyPlan = mock(UserProductSearchQualificationPlan.class);
        when(pendingPlan.missingTargets()).thenReturn(
                List.of(UserProductSearchQuestionTarget.SIZE, UserProductSearchQuestionTarget.SHIPS_TO));
        var pending = snapshot(
                oldQualificationId, userId, conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT, pendingPlan);
        var ready = new UserProductSearchQualificationSnapshot(
                newQualificationId,
                userId,
                conversationId,
                null,
                "digital course",
                UserProductSearchQualificationStatus.READY,
                readyPlan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, oldQualificationId)))
                .thenReturn(Optional.of(pending));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, newQualificationId)))
                .thenReturn(Optional.of(ready));
        when(qualificationService.qualify(any(), any())).thenReturn(new UserProductSearchQualificationResult(
                newQualificationId,
                UserProductSearchQualificationStatus.READY,
                "Ready",
                List.of(),
                List.of(),
                "digital course"
        ));
        when(readyPlan.missingFilters()).thenReturn(List.of());
        when(readyPlan.missingTargets()).thenReturn(List.of());
        when(readyPlan.effectiveQuery()).thenReturn("digital course");
        when(mapper.map(readyPlan)).thenReturn(new CatalogDiscoveryFilters(
                true, List.of(), null, List.of(), null, List.of(), List.of(), List.of(), null, List.of()));
        var service = service(qualificationService, persistenceService, mapper);

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, oldQualificationId, null, "Find a digital course"));

        ArgumentCaptor<QualifyUserProductSearchCommand> command =
                ArgumentCaptor.forClass(QualifyUserProductSearchCommand.class);
        verify(qualificationService).qualify(org.mockito.ArgumentMatchers.eq(profile), command.capture());
        assertThat(command.getValue().qualificationId()).isNull();
        assertThat(result.qualificationId()).isEqualTo(newQualificationId);
        assertThat(result.authoritativeQuery()).isEqualTo("digital course");
        verify(persistenceService).cancel(any(CancelUserProductSearchQualificationCommand.class));
    }

    @Test
    void cancelsAnExplicitPendingQualificationWithoutSearchingOrResumingIt() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);
        UserProductSearchQualificationSnapshot pending = snapshot(
                qualificationId, userId, conversationId,
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                mock(UserProductSearchQualificationPlan.class));
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(pending));
        var service = service(qualificationService, persistenceService, mapper);

        var result = service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, qualificationId, null, "Never mind"));

        assertThat(result.ready()).isFalse();
        assertThat(result.assistantMessage()).isEqualTo("Product search cancelled.");
        assertThat(result.qualificationId()).isNull();
        verify(persistenceService).cancel(any(CancelUserProductSearchQualificationCommand.class));
        verifyNoInteractions(qualificationService);
    }

    @Test
    void rejectsContinuationAcrossMerchantContexts() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UUID originalMerchant = UUID.randomUUID();
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationSnapshot pending = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                originalMerchant,
                "football boots",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(pending));
        var service = service(qualificationService, persistenceService, mapper);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);

        assertThatThrownBy(() -> service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, qualificationId, null, "Size 10")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Pending product-search qualification not found");
        verifyNoInteractions(qualificationService);
    }

    @Test
    void expiresAndCancelsAStaleExplicitContinuation() {
        UserProductSearchQualificationService qualificationService =
                mock(UserProductSearchQualificationService.class);
        UserProductSearchQualificationPersistenceService persistenceService =
                mock(UserProductSearchQualificationPersistenceService.class);
        UserProductSearchQualificationPlanMapper mapper = mock(UserProductSearchQualificationPlanMapper.class);
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID qualificationId = UUID.randomUUID();
        UserProductSearchQualificationPlan plan = mock(UserProductSearchQualificationPlan.class);
        UserProductSearchQualificationSnapshot stale = new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                UserProductSearchQualificationStatus.NEEDS_INPUT,
                plan,
                "model",
                "prompt",
                Instant.now().minus(Duration.ofHours(2)),
                Instant.now().minus(Duration.ofHours(2))
        );
        when(persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId)))
                .thenReturn(Optional.of(stale));
        var service = service(qualificationService, persistenceService, mapper);
        EnsureUserProfileCommand profile = new EnsureUserProfileCommand(
                userId, "shopper@example.test", "Shopper", null);

        assertThatThrownBy(() -> service.qualify(new QualifyAgentProductSearchCommand(
                profile, conversationId, null, qualificationId, null, "Size 10")))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("expired");
        verify(persistenceService).cancel(any(CancelUserProductSearchQualificationCommand.class));
        verifyNoInteractions(qualificationService);
    }

    private AgentProductSearchQualificationService service(
            UserProductSearchQualificationService qualificationService,
            UserProductSearchQualificationPersistenceService persistenceService,
            UserProductSearchQualificationPlanMapper mapper
    ) {
        AgentConversationRepository conversationRepository = mock(AgentConversationRepository.class);
        AgentConversation conversation = mock(AgentConversation.class);
        when(conversationRepository.findByIdAndUserId(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.of(conversation));
        when(conversation.getMerchantId()).thenReturn(null);
        return new AgentProductSearchQualificationService(
                conversationRepository,
                mock(AgentMessageRepository.class),
                qualificationService,
                persistenceService,
                mapper,
                new AgentProductSearchQualificationContinuationPolicy(new UserProductSearchCategoryPolicy()),
                properties(),
                agentProperties()
        );
    }

    private UserProductSearchQualificationSnapshot snapshot(
            UUID qualificationId,
            UUID userId,
            UUID conversationId,
            UserProductSearchQualificationStatus status,
            UserProductSearchQualificationPlan plan
    ) {
        return new UserProductSearchQualificationSnapshot(
                qualificationId,
                userId,
                conversationId,
                null,
                "football boots",
                status,
                plan,
                "model",
                "prompt",
                Instant.now(),
                Instant.now()
        );
    }

    private AgentMessage message(
            UUID conversationId,
            com.meant.api.module.agent.constant.AgentMessageRole role,
            long sequence,
            String text
    ) {
        AgentMessage message = mock(AgentMessage.class);
        when(message.getConversationId()).thenReturn(conversationId);
        when(message.getRole()).thenReturn(role);
        when(message.getSequenceNumber()).thenReturn(sequence);
        when(message.getTextContent()).thenReturn(text);
        return message;
    }

    private UserProductSearchProperties properties() {
        return new UserProductSearchProperties(
                "v1",
                "v1",
                "v1",
                4096,
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(30),
                10,
                5,
                12,
                Duration.ofHours(24),
                Duration.ofDays(7),
                6,
                3,
                80
        );
    }

    private AgentProperties agentProperties() {
        AgentProperties properties = mock(AgentProperties.class);
        when(properties.contextMessageBudget()).thenReturn(40);
        when(properties.contextCharacterBudget()).thenReturn(64_000);
        return properties;
    }
}

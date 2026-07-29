package com.meant.api.module.agent.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.repository.AgentMessageRepository;
import com.meant.api.module.agent.service.dto.AgentPendingProductSearchContinuation;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.agent.service.query.ResolveAgentPendingProductSearchQuery;
import com.meant.api.module.agent.service.command.QualifyAgentProductSearchCommand;
import com.meant.api.module.agent.constant.AgentMessageRole;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.FindUserProductSearchQualificationByRequestQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Applies the same validated qualification contract to agent searches before any catalog source runs.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class AgentProductSearchQualificationService {

    private static final int CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS = 32;
    private static final int MAXIMUM_CONVERSATION_MESSAGE_CHARACTERS = 4_000;

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserProductSearchQualificationService qualificationService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchQualificationPlanMapper planMapper;
    private final AgentProductSearchQualificationContinuationPolicy continuationPolicy;
    private final UserProductSearchProperties searchProperties;
    private final AgentProperties agentProperties;

    /**
     * Resolves a reply to a persisted qualification before the general agent model runs.
     *
     * <p>This keeps a short answer such as {@code 46} attached to the original shopping
     * request even when the outer model would otherwise treat it as a new catalog query.</p>
     */
    public Optional<AgentPendingProductSearchContinuation> resolvePendingContinuation(
            @NotNull @Valid ResolveAgentPendingProductSearchQuery query
    ) {
        requireOwnedConversation(query.userId(), query.conversationId(), query.merchantId());
        Continuation continuation = continuation(
                query.userId(),
                query.conversationId(),
                query.merchantId(),
                null,
                query.triggeringMessageId(),
                query.currentTurn().trim(),
                null
        );
        if (continuation.resolution() != ContinuationResolution.ANSWER
                && continuation.resolution() != ContinuationResolution.REQUEST_REPLAY) {
            return Optional.empty();
        }
        return Optional.of(new AgentPendingProductSearchContinuation(
                continuation.resumeId(),
                continuation.originalQuery(),
                continuation.observedUpdatedAt()
        ));
    }

    public AgentProductSearchQualificationResult qualify(
            @NotNull @Valid QualifyAgentProductSearchCommand command
    ) {
        var profile = command.profile();
        UUID conversationId = command.conversationId();
        UUID merchantId = command.merchantId();
        UUID qualificationId = command.qualificationId();
        UUID triggeringMessageId = command.triggeringMessageId();
        String authoritativeUserText = command.authoritativeUserText();
        QualificationStage stage = QualificationStage.OWNERSHIP;
        try {
            log.info(
                    "Agent product-search qualification started. userId={}, conversationId={}, merchantScoped={}, "
                            + "requestedQualificationId={}",
                    profile.id(),
                    conversationId,
                    merchantId != null,
                    qualificationId
            );
            requireOwnedConversation(profile.id(), conversationId, merchantId);
            String currentTurn = authoritativeUserText.trim();
            stage = QualificationStage.CONTINUATION;
            Continuation continuation = continuation(
                    profile.id(),
                    conversationId,
                    merchantId,
                    qualificationId,
                    triggeringMessageId,
                    currentTurn,
                    command.expectedQualificationUpdatedAt()
            );
            log.info(
                    "Agent product-search continuation resolved. userId={}, conversationId={}, resumeId={}, "
                            + "source={}, resolution={}, cancelled={}",
                    profile.id(),
                    conversationId,
                    continuation.resumeId(),
                    continuation.source(),
                    continuation.resolution(),
                    continuation.cancelled()
            );
            if (continuation.cancelled()) {
                return new AgentProductSearchQualificationResult(
                        null,
                        currentTurn,
                        "Product search cancelled.",
                        java.util.List.of(),
                        null,
                        java.util.Set.of()
                );
            }

            stage = QualificationStage.QUALIFICATION;
            var result = qualificationService.qualify(profile, new QualifyUserProductSearchCommand(
                    profile.id(),
                    conversationId,
                    continuation.resolution() == ContinuationResolution.REQUEST_REPLAY
                            ? null
                            : continuation.resumeId(),
                    currentTurn,
                    merchantId,
                    conversation(conversationId, triggeringMessageId),
                    triggeringMessageId,
                    continuation.resolution() == ContinuationResolution.REQUEST_REPLAY
                            ? null
                            : continuation.observedUpdatedAt(),
                    command.trustedReferenceProductText()
            ));
            log.info(
                    "Agent product-search qualification generated. userId={}, conversationId={}, "
                            + "qualificationId={}, status={}",
                    profile.id(),
                    conversationId,
                    result.qualificationId(),
                    result.status()
            );

            stage = QualificationStage.SNAPSHOT_READ;
            var snapshot = persistenceService.find(new GetUserProductSearchQualificationQuery(
                            profile.id(), result.qualificationId()))
                    .orElseThrow(() -> new IllegalStateException(
                            "Persisted agent product-search qualification was not found"));
            var plan = snapshot.plan();
            log.info(
                    "Agent product-search qualification snapshot loaded. userId={}, conversationId={}, "
                            + "qualificationId={}, persistedStatus={}, currentSchema={}, missingFilters={}, "
                            + "missingTargets={}",
                    profile.id(),
                    conversationId,
                    result.qualificationId(),
                    snapshot.status(),
                    plan.currentSchema(),
                    plan.missingFilters(),
                    plan.missingTargets()
            );
            if (!plan.missingFilters().isEmpty() || !plan.missingTargets().isEmpty()) {
                return new AgentProductSearchQualificationResult(
                        result.qualificationId(),
                        plan.effectiveQuery(),
                        plan.assistantMessage(),
                        plan.missingTargets(),
                        null,
                        plan.explicitAnyTargets(),
                        plan.profileSuppressionTargets()
                );
            }
            if (result.status() != UserProductSearchQualificationStatus.READY) {
                throw new IllegalStateException("Complete agent product-search qualification was not READY");
            }

            stage = QualificationStage.PLAN_MAPPING;
            var filters = planMapper.map(plan);
            log.info(
                    "Agent product-search qualification ready. userId={}, conversationId={}, qualificationId={}",
                    profile.id(),
                    conversationId,
                    result.qualificationId()
            );
            return new AgentProductSearchQualificationResult(
                    result.qualificationId(),
                    plan.effectiveQuery(),
                    plan.assistantMessage(),
                    plan.missingTargets(),
                    filters,
                    plan.explicitAnyTargets(),
                    plan.profileSuppressionTargets()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Agent product-search qualification failed. userId={}, conversationId={}, "
                            + "requestedQualificationId={}, stage={}, failureType={}, Error: {}",
                    profile.id(),
                    conversationId,
                    qualificationId,
                    stage,
                    exception.getClass().getName(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }

    private List<UserProductSearchConversationMessage> conversation(
            UUID conversationId,
            UUID triggeringMessageId
    ) {
        if (triggeringMessageId == null) {
            return List.of();
        }
        var triggering = messageRepository.findById(triggeringMessageId)
                .filter(message -> message.getConversationId().equals(conversationId))
                .filter(message -> message.getRole() == AgentMessageRole.USER
                        || message.getRole() == AgentMessageRole.USER_ACTION)
                .orElseThrow(AgentException::notFound);
        List<com.meant.api.module.agent.entity.AgentMessage> messages = new ArrayList<>(messageRepository
                .findBuyerVisibleConversationMessages(
                        conversationId,
                        triggering.getSequenceNumber(),
                        List.of(
                                AgentMessageRole.USER,
                                AgentMessageRole.USER_ACTION,
                                AgentMessageRole.ASSISTANT
                        ),
                        PageRequest.of(0, agentProperties.contextMessageBudget())
                ));
        int remainingCharacters = Math.max(1, agentProperties.contextCharacterBudget() / 2);
        List<UserProductSearchConversationMessage> newestFirst = new ArrayList<>();
        for (com.meant.api.module.agent.entity.AgentMessage message : messages) {
            int maximumCharacters = Math.min(
                    MAXIMUM_CONVERSATION_MESSAGE_CHARACTERS,
                    remainingCharacters - CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS
            );
            if (maximumCharacters <= 0) {
                break;
            }
            UserProductSearchConversationMessage projected =
                    conversationMessage(message, maximumCharacters);
            if (projected != null) {
                newestFirst.add(projected);
                remainingCharacters = Math.max(
                        0,
                        remainingCharacters
                                - projected.text().length()
                                - CONVERSATION_MESSAGE_OVERHEAD_CHARACTERS
                );
            }
        }
        java.util.Collections.reverse(newestFirst);
        return List.copyOf(newestFirst);
    }

    private UserProductSearchConversationMessage conversationMessage(
            com.meant.api.module.agent.entity.AgentMessage message,
            int maximumCharacters
    ) {
        if (message.getTextContent() == null || message.getTextContent().isBlank()) {
            return null;
        }
        UserProductSearchConversationMessage.Role role;
        if (message.getRole() == AgentMessageRole.USER
                || message.getRole() == AgentMessageRole.USER_ACTION) {
            role = UserProductSearchConversationMessage.Role.USER;
        } else if (message.getRole() == AgentMessageRole.ASSISTANT) {
            role = UserProductSearchConversationMessage.Role.ASSISTANT;
        } else {
            return null;
        }
        String text = message.getTextContent().trim();
        if (text.length() > maximumCharacters) {
            text = maximumCharacters == 1
                    ? "…"
                    : text.substring(0, maximumCharacters - 1) + "…";
        }
        return new UserProductSearchConversationMessage(role, text);
    }

    private void requireOwnedConversation(UUID userId, UUID conversationId, UUID merchantId) {
        var conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(AgentException::notFound);
        if (!Objects.equals(conversation.getMerchantId(), merchantId)) {
            throw AgentException.notFound();
        }
    }

    private Continuation continuation(
            UUID userId,
            UUID conversationId,
            UUID merchantId,
            UUID qualificationId,
            UUID requestId,
            String currentTurn,
            Instant expectedUpdatedAt
    ) {
        boolean inferred = qualificationId == null;
        if (requestId != null) {
            var durableRequestReplay = persistenceService.findByRequest(
                    new FindUserProductSearchQualificationByRequestQuery(
                            userId,
                            conversationId,
                            merchantId,
                            requestId,
                            currentTurn
                    )
            );
            if (durableRequestReplay.isPresent()) {
                var replay = durableRequestReplay.get();
                if (qualificationId != null && !qualificationId.equals(replay.qualificationId())) {
                    throw UserException.conflict(
                            "Product-search qualification request identity was already used");
                }
                return new Continuation(
                        replay.qualificationId(),
                        replay.originalQuery(),
                        false,
                        ContinuationSource.REQUEST,
                        ContinuationResolution.REQUEST_REPLAY,
                        replay.updatedAt()
                );
            }
        }
        if (inferred && requestId != null) {
            QualifyUserProductSearchCommand replayCommand = new QualifyUserProductSearchCommand(
                    userId,
                    conversationId,
                    null,
                    currentTurn,
                    merchantId,
                    List.of(),
                    requestId
            );
            var requestReplay = persistenceService.find(new GetUserProductSearchQualificationQuery(
                    userId,
                    replayCommand.requestQualificationId()
            ));
            if (requestReplay.isPresent()) {
                var replay = requestReplay.get();
                return new Continuation(
                        replay.qualificationId(),
                        replay.originalQuery(),
                        false,
                        ContinuationSource.REQUEST,
                        ContinuationResolution.REQUEST_REPLAY,
                        replay.updatedAt()
                );
            }
        }
        var pending = inferred
                ? persistenceService.findLatestPending(new FindPendingUserProductSearchQualificationQuery(
                        userId, conversationId, merchantId))
                : persistenceService.find(new GetUserProductSearchQualificationQuery(userId, qualificationId));
        if (pending.isEmpty()) {
            if (inferred) {
                return new Continuation(
                        null,
                        null,
                        false,
                        ContinuationSource.NONE,
                        ContinuationResolution.START_NEW,
                        null
                );
            }
            throw UserException.notFound("Product-search qualification not found");
        }
        var snapshot = pending.get();
        if (!snapshot.conversationId().equals(conversationId)
                || !Objects.equals(snapshot.merchantId(), merchantId)) {
            throw UserException.notFound("Pending product-search qualification not found");
        }
        if (expectedUpdatedAt != null
                && (snapshot.status() != UserProductSearchQualificationStatus.NEEDS_INPUT
                        || !snapshot.updatedAt().equals(expectedUpdatedAt))) {
            throw UserException.conflict(
                    "Product-search qualification changed before this answer could be applied");
        }
        if (!inferred && snapshot.status() == UserProductSearchQualificationStatus.READY) {
            if (requestId != null) {
                throw UserException.conflict(
                        "Product-search qualification changed before this request could be bound");
            }
            return new Continuation(
                    snapshot.qualificationId(),
                    snapshot.originalQuery(),
                    false,
                    ContinuationSource.REQUESTED,
                    ContinuationResolution.READY_REPLAY,
                    null
            );
        }
        if (snapshot.status() != UserProductSearchQualificationStatus.NEEDS_INPUT) {
            throw UserException.notFound("Pending product-search qualification not found");
        }
        if (snapshot.updatedAt().plus(searchProperties.qualificationPendingTtl()).isBefore(Instant.now())) {
            cancel(snapshot);
            if (inferred) {
                return new Continuation(
                        null,
                        null,
                        false,
                        ContinuationSource.INFERRED,
                        ContinuationResolution.EXPIRED,
                        null
                );
            }
            throw UserException.notFound("Product-search qualification expired; repeat the shopping request");
        }
        ContinuationSource source = inferred ? ContinuationSource.INFERRED : ContinuationSource.REQUESTED;
        return switch (continuationPolicy.decide(snapshot, currentTurn)) {
            case ANSWER -> new Continuation(
                    snapshot.qualificationId(),
                    snapshot.originalQuery(),
                    false,
                    source,
                    ContinuationResolution.ANSWER,
                    snapshot.updatedAt()
            );
            case CANCEL -> {
                cancel(snapshot);
                yield new Continuation(null, null, true, source, ContinuationResolution.CANCEL, null);
            }
            case NEW_INTENT -> {
                cancel(snapshot);
                yield new Continuation(null, null, false, source, ContinuationResolution.NEW_INTENT, null);
            }
            case PASS_THROUGH -> new Continuation(
                    null,
                    null,
                    false,
                    source,
                    ContinuationResolution.PASS_THROUGH,
                    null
            );
        };
    }

    private void cancel(com.meant.api.module.user.service.dto.UserProductSearchQualificationSnapshot snapshot) {
        persistenceService.cancel(new CancelUserProductSearchQualificationCommand(
                snapshot.qualificationId(),
                snapshot.userId(),
                snapshot.conversationId(),
                snapshot.merchantId(),
                snapshot.updatedAt()
        ));
    }

    private record Continuation(
            UUID resumeId,
            String originalQuery,
            boolean cancelled,
            ContinuationSource source,
            ContinuationResolution resolution,
            Instant observedUpdatedAt
    ) {
    }

    private enum ContinuationSource {
        NONE,
        REQUEST,
        REQUESTED,
        INFERRED
    }

    private enum ContinuationResolution {
        START_NEW,
        REQUEST_REPLAY,
        READY_REPLAY,
        ANSWER,
        CANCEL,
        NEW_INTENT,
        EXPIRED,
        PASS_THROUGH
    }

    private enum QualificationStage {
        OWNERSHIP,
        CONTINUATION,
        QUALIFICATION,
        SNAPSHOT_READ,
        PLAN_MAPPING
    }
}

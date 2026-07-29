package com.meant.api.module.agent.service;

import com.meant.api.module.agent.exception.AgentException;
import com.meant.api.module.agent.repository.AgentConversationRepository;
import com.meant.api.module.agent.service.dto.AgentProductSearchQualificationResult;
import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.UserProductSearchQualificationPersistenceService;
import com.meant.api.module.user.service.UserProductSearchQualificationPlanMapper;
import com.meant.api.module.user.service.UserProductSearchQualificationService;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final AgentConversationRepository conversationRepository;
    private final UserProductSearchQualificationService qualificationService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchQualificationPlanMapper planMapper;
    private final AgentProductSearchQualificationContinuationPolicy continuationPolicy;
    private final UserProductSearchProperties searchProperties;

    public AgentProductSearchQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profile,
            @NotNull UUID conversationId,
            UUID merchantId,
            UUID qualificationId,
            @NotBlank String authoritativeUserText
    ) {
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
                    profile.id(), conversationId, merchantId, qualificationId, currentTurn);
            log.info(
                    "Agent product-search continuation resolved. userId={}, conversationId={}, resumeId={}, "
                            + "cancelled={}",
                    profile.id(),
                    conversationId,
                    continuation.resumeId(),
                    continuation.cancelled()
            );
            if (continuation.cancelled()) {
                return new AgentProductSearchQualificationResult(
                        null,
                        currentTurn,
                        "Product search cancelled.",
                        java.util.List.of(),
                        null
                );
            }

            stage = QualificationStage.QUALIFICATION;
            var result = qualificationService.qualify(profile, new QualifyUserProductSearchCommand(
                    profile.id(),
                    conversationId,
                    continuation.resumeId(),
                    currentTurn,
                    merchantId
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
                            + "qualificationId={}, persistedStatus={}, currentSchema={}, missingFilterCount={}, "
                            + "missingTargetCount={}",
                    profile.id(),
                    conversationId,
                    result.qualificationId(),
                    snapshot.status(),
                    plan.currentSchema(),
                    plan.missingFilters().size(),
                    plan.missingTargets().size()
            );
            if (!plan.missingFilters().isEmpty() || !plan.missingTargets().isEmpty()) {
                return new AgentProductSearchQualificationResult(
                        result.qualificationId(),
                        snapshot.originalQuery(),
                        plan.assistantMessage(),
                        plan.missingTargets(),
                        null
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
                    snapshot.originalQuery(),
                    plan.assistantMessage(),
                    plan.missingTargets(),
                    filters
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
            String currentTurn
    ) {
        if (qualificationId == null) {
            return new Continuation(null, false);
        }
        var snapshot = persistenceService.find(
                        new GetUserProductSearchQualificationQuery(userId, qualificationId))
                .orElseThrow(() -> UserException.notFound("Product-search qualification not found"));
        if (!snapshot.conversationId().equals(conversationId)
                || !Objects.equals(snapshot.merchantId(), merchantId)
                || snapshot.status() != UserProductSearchQualificationStatus.NEEDS_INPUT) {
            throw UserException.notFound("Pending product-search qualification not found");
        }
        if (snapshot.updatedAt().plus(searchProperties.qualificationPendingTtl()).isBefore(Instant.now())) {
            cancel(snapshot);
            throw UserException.notFound("Product-search qualification expired; repeat the shopping request");
        }
        return switch (continuationPolicy.decide(snapshot, currentTurn)) {
            case ANSWER -> new Continuation(qualificationId, false);
            case CANCEL -> {
                cancel(snapshot);
                yield new Continuation(null, true);
            }
            case NEW_INTENT -> {
                cancel(snapshot);
                yield new Continuation(null, false);
            }
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

    private record Continuation(UUID resumeId, boolean cancelled) {
    }

    private enum QualificationStage {
        OWNERSHIP,
        CONTINUATION,
        QUALIFICATION,
        SNAPSHOT_READ,
        PLAN_MAPPING
    }
}

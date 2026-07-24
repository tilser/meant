package com.meant.api.module.user.service;

import com.meant.api.module.user.service.command.CancelUserProductSearchQualificationCommand;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.UserProductSearchAgentQualificationResult;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Applies the same validated qualification contract to agent searches before any catalog source runs.
 */
@Service
@Validated
@RequiredArgsConstructor
public class UserProductSearchAgentQualificationService {

    private final UserProductSearchQualificationService qualificationService;
    private final UserProductSearchQualificationPersistenceService persistenceService;
    private final UserProductSearchQualificationPlanMapper planMapper;
    private final UserProductSearchQualificationContinuationPolicy continuationPolicy;
    private final UserProductSearchProperties searchProperties;

    public UserProductSearchAgentQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profile,
            @NotNull UUID conversationId,
            UUID merchantId,
            UUID qualificationId,
            @NotBlank String authoritativeUserText
    ) {
        String currentTurn = authoritativeUserText.trim();
        Continuation continuation = continuation(
                profile.id(), conversationId, merchantId, qualificationId, currentTurn);
        if (continuation.cancelled()) {
            return new UserProductSearchAgentQualificationResult(
                    null,
                    currentTurn,
                    "Product search cancelled.",
                    java.util.List.of(),
                    null
            );
        }
        var result = qualificationService.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(),
                conversationId,
                continuation.resumeId(),
                currentTurn,
                merchantId
        ));
        var snapshot = persistenceService.find(new GetUserProductSearchQualificationQuery(
                        profile.id(), result.qualificationId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted agent product-search qualification was not found"));
        var plan = snapshot.plan();
        if (!plan.missingFilters().isEmpty() || !plan.missingTargets().isEmpty()) {
            return new UserProductSearchAgentQualificationResult(
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
        return new UserProductSearchAgentQualificationResult(
                result.qualificationId(),
                snapshot.originalQuery(),
                plan.assistantMessage(),
                plan.missingTargets(),
                planMapper.map(plan)
        );
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
}

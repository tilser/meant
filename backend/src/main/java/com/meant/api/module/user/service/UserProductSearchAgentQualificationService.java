package com.meant.api.module.user.service;

import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.QualifyUserProductSearchCommand;
import com.meant.api.module.user.constant.UserProductSearchQualificationStatus;
import com.meant.api.module.user.service.dto.UserProductSearchAgentQualificationResult;
import com.meant.api.module.user.service.query.FindPendingUserProductSearchQualificationQuery;
import com.meant.api.module.user.service.query.GetUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    public UserProductSearchAgentQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profile,
            @NotNull UUID conversationId,
            UUID merchantId,
            @NotBlank String authoritativeUserText
    ) {
        UUID pendingQualificationId = persistenceService.findLatestPending(
                        new FindPendingUserProductSearchQualificationQuery(
                                profile.id(), conversationId, merchantId))
                .map(snapshot -> snapshot.qualificationId())
                .orElse(null);
        var result = qualificationService.qualify(profile, new QualifyUserProductSearchCommand(
                profile.id(),
                conversationId,
                pendingQualificationId,
                authoritativeUserText.trim(),
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
                    plan.effectiveQuery(),
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
                plan.effectiveQuery(),
                plan.assistantMessage(),
                plan.missingTargets(),
                planMapper.map(plan)
        );
    }
}

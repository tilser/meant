package com.meant.api.module.user.service;

import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.dto.UserProductSearchAgentQualificationResult;
import com.meant.api.module.user.service.query.GenerateUserProductSearchQualificationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

    private final UserSettingsService userSettingsService;
    private final UserProductSearchPreferenceService preferenceService;
    private final UserProductSearchQualificationModelService modelService;
    private final UserProductSearchQualificationPlanMapper planMapper;

    public UserProductSearchAgentQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profile,
            @NotBlank String authoritativeUserText,
            @NotBlank String proposedCatalogQuery
    ) {
        var generated = modelService.generate(new GenerateUserProductSearchQualificationQuery(
                authoritativeUserText.trim(),
                authoritativeUserText.trim(),
                null,
                userSettingsService.get(profile),
                preferenceService.list(profile.id()),
                proposedCatalogQuery.trim()
        ));
        var plan = generated.plan();
        if (!plan.missingFilters().isEmpty() || !plan.missingTargets().isEmpty()) {
            return new UserProductSearchAgentQualificationResult(
                    plan.effectiveQuery(),
                    plan.assistantMessage(),
                    plan.missingTargets(),
                    null
            );
        }
        return new UserProductSearchAgentQualificationResult(
                plan.effectiveQuery(),
                plan.assistantMessage(),
                List.of(),
                planMapper.map(plan)
        );
    }

    public UserProductSearchAgentQualificationResult qualify(
            @NotNull @Valid EnsureUserProfileCommand profile,
            @NotBlank String query
    ) {
        return qualify(profile, query, query);
    }
}

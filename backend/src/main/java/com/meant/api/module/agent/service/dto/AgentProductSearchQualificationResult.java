package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-validated qualification outcome for one agent catalog-search attempt. */
public record AgentProductSearchQualificationResult(
        UUID qualificationId,
        String authoritativeQuery,
        String assistantMessage,
        List<UserProductSearchQuestionTarget> questionTargets,
        CatalogDiscoveryFilters filters,
        Set<UserProductSearchQuestionTarget> explicitAnyTargets,
        Set<UserProductSearchQuestionTarget> profileSuppressionTargets
) {

    public AgentProductSearchQualificationResult {
        questionTargets = questionTargets == null ? List.of() : List.copyOf(questionTargets);
        explicitAnyTargets = explicitAnyTargets == null ? Set.of() : Set.copyOf(explicitAnyTargets);
        profileSuppressionTargets = profileSuppressionTargets == null
                ? Set.of()
                : Set.copyOf(profileSuppressionTargets);
    }

    public AgentProductSearchQualificationResult(
            UUID qualificationId,
            String authoritativeQuery,
            String assistantMessage,
            List<UserProductSearchQuestionTarget> questionTargets,
            CatalogDiscoveryFilters filters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets
    ) {
        this(
                qualificationId,
                authoritativeQuery,
                assistantMessage,
                questionTargets,
                filters,
                explicitAnyTargets,
                explicitAnyTargets
        );
    }

    public AgentProductSearchQualificationResult(
            UUID qualificationId,
            String authoritativeQuery,
            String assistantMessage,
            List<UserProductSearchQuestionTarget> questionTargets,
            CatalogDiscoveryFilters filters
    ) {
        this(
                qualificationId,
                authoritativeQuery,
                assistantMessage,
                questionTargets,
                filters,
                Set.of(),
                Set.of()
        );
    }

    public boolean ready() {
        return questionTargets.isEmpty() && filters != null;
    }
}

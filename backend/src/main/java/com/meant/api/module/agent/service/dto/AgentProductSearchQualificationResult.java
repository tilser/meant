package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-validated search advice. Missing dimensions never prevent catalog execution. */
public record AgentProductSearchQualificationResult(
        UUID qualificationId,
        String authoritativeQuery,
        CatalogDiscoveryFilters filters,
        Map<String, AgentAppliedSearchFilter> appliedFilters,
        List<UserProductSearchQuestionTarget> unsetFilters,
        Set<UserProductSearchQuestionTarget> explicitAnyTargets,
        Set<UserProductSearchQuestionTarget> profileSuppressionTargets
) {

    public AgentProductSearchQualificationResult {
        appliedFilters = appliedFilters == null ? Map.of() : Map.copyOf(appliedFilters);
        unsetFilters = unsetFilters == null ? List.of() : List.copyOf(unsetFilters);
        explicitAnyTargets = explicitAnyTargets == null ? Set.of() : Set.copyOf(explicitAnyTargets);
        profileSuppressionTargets = profileSuppressionTargets == null
                ? Set.of()
                : Set.copyOf(profileSuppressionTargets);
    }

    public boolean ready() {
        return filters != null;
    }
}

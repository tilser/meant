package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;

/** Server-validated qualification outcome for one agent catalog-search attempt. */
public record UserProductSearchAgentQualificationResult(
        String effectiveQuery,
        String assistantMessage,
        List<UserProductSearchQuestionTarget> questionTargets,
        CatalogDiscoveryFilters filters
) {

    public UserProductSearchAgentQualificationResult {
        questionTargets = questionTargets == null ? List.of() : List.copyOf(questionTargets);
    }

    public boolean ready() {
        return questionTargets.isEmpty() && filters != null;
    }
}

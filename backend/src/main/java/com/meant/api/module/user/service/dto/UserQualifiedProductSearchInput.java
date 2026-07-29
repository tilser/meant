package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.Set;
import java.util.UUID;

public record UserQualifiedProductSearchInput(
        UUID qualificationId,
        UUID conversationId,
        UUID merchantId,
        String effectiveQuery,
        CatalogDiscoveryFilters filters,
        Set<UserProductSearchQuestionTarget> explicitAnyTargets,
        Set<UserProductSearchQuestionTarget> profileSuppressionTargets
) {

    public UserQualifiedProductSearchInput {
        explicitAnyTargets = explicitAnyTargets == null ? Set.of() : Set.copyOf(explicitAnyTargets);
        profileSuppressionTargets = profileSuppressionTargets == null
                ? Set.of()
                : Set.copyOf(profileSuppressionTargets);
    }

    public UserQualifiedProductSearchInput(
            UUID qualificationId,
            UUID conversationId,
            UUID merchantId,
            String effectiveQuery,
            CatalogDiscoveryFilters filters,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets
    ) {
        this(
                qualificationId,
                conversationId,
                merchantId,
                effectiveQuery,
                filters,
                explicitAnyTargets,
                explicitAnyTargets
        );
    }

    public UserQualifiedProductSearchInput(
            UUID qualificationId,
            UUID conversationId,
            UUID merchantId,
            String effectiveQuery,
            CatalogDiscoveryFilters filters
    ) {
        this(
                qualificationId,
                conversationId,
                merchantId,
                effectiveQuery,
                filters,
                Set.of(),
                Set.of()
        );
    }
}

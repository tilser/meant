package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.time.Instant;
import java.util.Set;

public record UserProductSearchPreparation(
        String query,
        UserProductSearchQueryIntentResult queryIntent,
        UserSettingsResult settings,
        UserTasteProfileResult tasteProfile,
        UserProductSearchCatalogInput catalogInput,
        String normalizedQuery,
        String profileHash,
        Instant now,
        int offset,
        int limit,
        int fetchLimit,
        Set<UserProductSearchQuestionTarget> explicitAnyTargets,
        Set<UserProductSearchQuestionTarget> profileSuppressionTargets
) {

    public UserProductSearchPreparation {
        explicitAnyTargets = explicitAnyTargets == null ? Set.of() : Set.copyOf(explicitAnyTargets);
        profileSuppressionTargets = profileSuppressionTargets == null
                ? Set.of()
                : Set.copyOf(profileSuppressionTargets);
    }

    public UserProductSearchPreparation(
            String query,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings,
            UserTasteProfileResult tasteProfile,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            Instant now,
            int offset,
            int limit,
            int fetchLimit,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets
    ) {
        this(
                query,
                queryIntent,
                settings,
                tasteProfile,
                catalogInput,
                normalizedQuery,
                profileHash,
                now,
                offset,
                limit,
                fetchLimit,
                explicitAnyTargets,
                explicitAnyTargets
        );
    }

    public UserProductSearchPreparation(
            String query,
            UserProductSearchQueryIntentResult queryIntent,
            UserSettingsResult settings,
            UserTasteProfileResult tasteProfile,
            UserProductSearchCatalogInput catalogInput,
            String normalizedQuery,
            String profileHash,
            Instant now,
            int offset,
            int limit,
            int fetchLimit
    ) {
        this(
                query,
                queryIntent,
                settings,
                tasteProfile,
                catalogInput,
                normalizedQuery,
                profileHash,
                now,
                offset,
                limit,
                fetchLimit,
                Set.of(),
                Set.of()
        );
    }
}

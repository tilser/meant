package com.meant.api.module.user.service.query;

import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record GenerateUserProductSearchQualificationQuery(
        @NotBlank String originalQuery,
        @NotBlank String message,
        UserProductSearchQualificationPlan previousPlan,
        @NotNull UserSettingsResult settings,
        @NotNull List<UserProductSearchPreferenceResult> durablePreferences,
        String catalogQueryHint
) {

    public GenerateUserProductSearchQualificationQuery {
        durablePreferences = durablePreferences == null ? List.of() : List.copyOf(durablePreferences);
    }

    /** Backwards-compatible constructor for callers without durable preference context. */
    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> durablePreferences
    ) {
        this(originalQuery, message, previousPlan, settings, durablePreferences, null);
    }

    /** Backwards-compatible constructor for callers without durable preference context. */
    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings
    ) {
        this(originalQuery, message, previousPlan, settings, List.of(), null);
    }
}

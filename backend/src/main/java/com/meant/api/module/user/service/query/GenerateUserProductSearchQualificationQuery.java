package com.meant.api.module.user.service.query;

import com.meant.api.module.user.service.dto.UserProductSearchQualificationPlan;
import com.meant.api.module.user.service.dto.UserProductSearchConversationMessage;
import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GenerateUserProductSearchQualificationQuery(
        @NotBlank String originalQuery,
        @NotBlank String message,
        UserProductSearchQualificationPlan previousPlan,
        @NotNull UserSettingsResult settings,
        @NotNull List<UserProductSearchPreferenceResult> durablePreferences,
        @NotNull List<@Valid UserProductSearchConversationMessage> conversation,
        @NotNull UserTasteProfileResult tasteProfile,
        @Size(max = 500) String trustedReferenceProductText
) {

    public GenerateUserProductSearchQualificationQuery {
        durablePreferences = durablePreferences == null ? List.of() : List.copyOf(durablePreferences);
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        tasteProfile = tasteProfile == null
                ? new UserTasteProfileResult(null, List.of(), List.of())
                : new UserTasteProfileResult(
                        tasteProfile.profileHash(),
                        tasteProfile.signals() == null ? List.of() : List.copyOf(tasteProfile.signals()),
                        tasteProfile.suggestions() == null ? List.of() : List.copyOf(tasteProfile.suggestions())
                );
        trustedReferenceProductText = trustedReferenceProductText == null
                        || trustedReferenceProductText.isBlank()
                ? null
                : trustedReferenceProductText.trim();
    }

    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> durablePreferences,
            List<UserProductSearchConversationMessage> conversation,
            UserTasteProfileResult tasteProfile
    ) {
        this(
                originalQuery,
                message,
                previousPlan,
                settings,
                durablePreferences,
                conversation,
                tasteProfile,
                null
        );
    }

    /** Backwards-compatible constructor for callers without taste-profile context. */
    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> durablePreferences,
            List<UserProductSearchConversationMessage> conversation
    ) {
        this(
                originalQuery,
                message,
                previousPlan,
                settings,
                durablePreferences,
                conversation,
                new UserTasteProfileResult(null, List.of(), List.of()),
                null
        );
    }

    /** Backwards-compatible constructor for callers without conversation or taste-profile context. */
    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings,
            List<UserProductSearchPreferenceResult> durablePreferences
    ) {
        this(originalQuery, message, previousPlan, settings, durablePreferences, List.of());
    }

    /** Backwards-compatible constructor for callers without durable, conversation, or taste context. */
    public GenerateUserProductSearchQualificationQuery(
            String originalQuery,
            String message,
            UserProductSearchQualificationPlan previousPlan,
            UserSettingsResult settings
    ) {
        this(originalQuery, message, previousPlan, settings, List.of());
    }
}

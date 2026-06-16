package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.UserSettings;
import java.time.Instant;
import java.util.List;

public record UserSettingsResult(
        Integer budget,
        UserLocationResult location,
        List<ShoppingFilterResult> filters,
        List<ShoppingFilterResult> availableFilters,
        List<String> parsedFilterIds,
        List<String> unmappedPreferences,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserSettingsResult from(
            UserSettings settings,
            List<ShoppingFilterResult> filters,
            List<ShoppingFilterResult> availableFilters,
            List<String> parsedFilterIds,
            List<String> unmappedPreferences
    ) {
        return new UserSettingsResult(
                settings.getBudget(),
                UserLocationResult.from(settings),
                filters,
                availableFilters,
                parsedFilterIds,
                unmappedPreferences,
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}

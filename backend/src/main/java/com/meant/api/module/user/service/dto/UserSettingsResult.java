package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserCurrency;
import com.meant.api.module.user.entity.UserSettings;
import java.time.Instant;
import java.util.List;

public record UserSettingsResult(
        Integer budget,
        String currency,
        String clothingFit,
        UserLocationResult location,
        List<UserLocationResult> locations,
        List<ShoppingFilterResult> filters,
        List<ShoppingFilterResult> availableFilters,
        List<String> parsedFilterIds,
        List<String> unmappedPreferences,
        Instant createdAt,
        Instant updatedAt
) {

    public UserSettingsResult(
            Integer budget,
            String clothingFit,
            UserLocationResult location,
            List<UserLocationResult> locations,
            List<ShoppingFilterResult> filters,
            List<ShoppingFilterResult> availableFilters,
            List<String> parsedFilterIds,
            List<String> unmappedPreferences,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                budget,
                UserCurrency.DEFAULT,
                clothingFit,
                location,
                locations,
                filters,
                availableFilters,
                parsedFilterIds,
                unmappedPreferences,
                createdAt,
                updatedAt
        );
    }

    public static UserSettingsResult from(
            UserSettings settings,
            List<UserLocationResult> locations,
            List<ShoppingFilterResult> filters,
            List<ShoppingFilterResult> availableFilters,
            List<String> parsedFilterIds,
            List<String> unmappedPreferences
    ) {
        UserLocationResult location = locations.isEmpty() ? null : locations.get(0);
        return new UserSettingsResult(
                settings.getBudget(),
                UserCurrency.normalizeOrDefault(settings.getCurrency()),
                settings.getClothingFit(),
                location,
                locations,
                filters,
                availableFilters,
                parsedFilterIds,
                unmappedPreferences,
                settings.getCreatedAt(),
                settings.getUpdatedAt()
        );
    }
}

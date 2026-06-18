package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.List;

public record UserSettingsResponse(
        Integer budget,
        String clothingFit,
        UserLocationResponse location,
        List<UserLocationResponse> locations,
        List<ShoppingFilterResponse> filters,
        List<ShoppingFilterResponse> availableFilters,
        List<String> parsedFilterIds,
        List<String> unmappedPreferences,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserSettingsResponse from(UserSettingsResult result) {
        return new UserSettingsResponse(
                result.budget(),
                result.clothingFit(),
                UserLocationResponse.from(result.location()),
                result.locations().stream().map(UserLocationResponse::from).toList(),
                result.filters().stream().map(ShoppingFilterResponse::from).toList(),
                result.availableFilters().stream().map(ShoppingFilterResponse::from).toList(),
                result.parsedFilterIds(),
                result.unmappedPreferences(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}

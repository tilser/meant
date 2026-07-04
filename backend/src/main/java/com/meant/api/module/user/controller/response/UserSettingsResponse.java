package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserSettingsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record UserSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer budget,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String clothingFit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserLocationResponse location,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserLocationResponse> locations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ShoppingFilterResponse> filters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ShoppingFilterResponse> availableFilters,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> parsedFilterIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> unmappedPreferences,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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

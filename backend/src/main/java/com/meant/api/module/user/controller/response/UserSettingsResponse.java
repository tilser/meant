package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchPreferenceResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record UserSettingsResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer budget,
        @Schema(
                description = "Preferred ISO 4217 currency used for catalog prices",
                example = "USD",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String currency,
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
        @Schema(
                description = "Stable product-scoped values learned during search qualification",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        List<UserProductSearchPreferenceResponse> productSearchPreferences,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {

    public static UserSettingsResponse from(
            UserSettingsResult result,
            List<UserProductSearchPreferenceResult> productSearchPreferences
    ) {
        return new UserSettingsResponse(
                result.budget(),
                result.currency(),
                result.clothingFit(),
                UserLocationResponse.from(result.location()),
                result.locations().stream().map(UserLocationResponse::from).toList(),
                result.filters().stream().map(ShoppingFilterResponse::from).toList(),
                result.availableFilters().stream().map(ShoppingFilterResponse::from).toList(),
                result.parsedFilterIds(),
                result.unmappedPreferences(),
                productSearchPreferences.stream()
                        .map(UserProductSearchPreferenceResponse::from)
                        .toList(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}

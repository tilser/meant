package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserLocationResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserLocationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "geonames:3067696")
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String country,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(
                description = "UCP ships_to.region value",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String region,
        @Schema(
                description = "UCP ships_to.postal_code value",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String postalCode,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String regionName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String city
) {

    public static UserLocationResponse from(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        return new UserLocationResponse(
                location.id(),
                location.country(),
                location.code(),
                location.region(),
                location.postalCode(),
                location.regionName(),
                location.city()
        );
    }
}

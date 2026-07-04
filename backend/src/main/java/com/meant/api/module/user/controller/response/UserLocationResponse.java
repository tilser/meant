package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserLocationResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserLocationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String country,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String city
) {

    public static UserLocationResponse from(UserLocationResult location) {
        if (location == null) {
            return null;
        }
        return new UserLocationResponse(location.country(), location.code(), location.city());
    }
}

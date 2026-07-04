package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartMessageResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record CartMessageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String severity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String target
) {

    public static CartMessageResponse from(CartMessageResult result) {
        if (result == null) {
            return null;
        }
        return new CartMessageResponse(
                result.code(),
                result.severity(),
                result.type(),
                result.message(),
                result.target()
        );
    }
}

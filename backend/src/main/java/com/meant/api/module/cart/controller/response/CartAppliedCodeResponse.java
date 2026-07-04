package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.service.dto.CartAppliedCodeResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record CartAppliedCodeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CartAppliedCodeType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean applicable,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency
) {

    public static CartAppliedCodeResponse from(CartAppliedCodeResult result) {
        return new CartAppliedCodeResponse(
                result.type(),
                result.code(),
                result.label(),
                result.applicable(),
                result.amount(),
                result.currency()
        );
    }
}

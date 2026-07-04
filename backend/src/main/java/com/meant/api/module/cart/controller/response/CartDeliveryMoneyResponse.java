package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartDeliveryMoneyResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record CartDeliveryMoneyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String currency
) {

    public static CartDeliveryMoneyResponse from(CartDeliveryMoneyResult result) {
        return result == null ? null : new CartDeliveryMoneyResponse(result.amount(), result.currency());
    }
}

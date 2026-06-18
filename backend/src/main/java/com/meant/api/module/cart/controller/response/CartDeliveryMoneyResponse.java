package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartDeliveryMoneyResult;

public record CartDeliveryMoneyResponse(
        String amount,
        String currency
) {

    public static CartDeliveryMoneyResponse from(CartDeliveryMoneyResult result) {
        return result == null ? null : new CartDeliveryMoneyResponse(result.amount(), result.currency());
    }
}

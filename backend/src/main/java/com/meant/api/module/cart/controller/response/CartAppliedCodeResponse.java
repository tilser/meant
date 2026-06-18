package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.service.dto.CartAppliedCodeResult;

public record CartAppliedCodeResponse(
        CartAppliedCodeType type,
        String code,
        String label,
        Boolean applicable,
        String amount,
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

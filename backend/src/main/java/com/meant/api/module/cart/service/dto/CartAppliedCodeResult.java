package com.meant.api.module.cart.service.dto;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.CartAppliedCode;

public record CartAppliedCodeResult(
        CartAppliedCodeType type,
        String code,
        String label,
        Boolean applicable,
        String amount,
        String currency
) {

    public static CartAppliedCodeResult from(CartAppliedCode appliedCode) {
        return new CartAppliedCodeResult(
                appliedCode.getType(),
                appliedCode.getCode(),
                appliedCode.getLabel(),
                appliedCode.getApplicable(),
                appliedCode.getAmount(),
                appliedCode.getCurrency()
        );
    }
}

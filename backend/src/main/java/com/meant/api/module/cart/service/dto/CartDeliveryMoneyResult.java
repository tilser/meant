package com.meant.api.module.cart.service.dto;

public record CartDeliveryMoneyResult(
        String amount,
        String currency
) {

    public static CartDeliveryMoneyResult from(CartToolResponse.Money money) {
        return money == null ? null : new CartDeliveryMoneyResult(money.amount(), money.currency());
    }
}

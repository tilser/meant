package com.meant.api.module.cart.service.dto;

import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.support.UcpCartMoney;

public record CartDeliveryMoneyResult(
        String amount,
        String currency
) {

    public static CartDeliveryMoneyResult from(UcpCartResponse.Money money) {
        return money == null ? null : new CartDeliveryMoneyResult(UcpCartMoney.displayAmount(money), money.currency());
    }
}

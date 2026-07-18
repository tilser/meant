package com.meant.api.plugin.cart.common.support;

import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.support.UcpDecimal;
import java.math.BigDecimal;

public final class UcpCartMoney {

    private UcpCartMoney() {
    }

    public static String displayAmount(UcpCartResponse.Money money) {
        if (money == null || money.amount() == null) {
            return null;
        }
        String currency = money.currency();
        int exponent = UcpDecimal.currencyExponent(currency);
        return BigDecimal.valueOf(money.amount(), exponent)
                .setScale(exponent)
                .toPlainString();
    }

    public static String currency(UcpCartResponse.Money first, UcpCartResponse.Money second) {
        return currency(first, second, null);
    }

    public static String currency(
            UcpCartResponse.Money first,
            UcpCartResponse.Money second,
            String fallback
    ) {
        if (first != null && first.currency() != null && !first.currency().isBlank()) {
            return first.currency();
        }
        if (second != null && second.currency() != null && !second.currency().isBlank()) {
            return second.currency();
        }
        return fallback;
    }
}

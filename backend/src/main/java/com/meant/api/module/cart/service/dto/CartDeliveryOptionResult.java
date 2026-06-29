package com.meant.api.module.cart.service.dto;

import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import java.time.Instant;

public record CartDeliveryOptionResult(
        String handle,
        String title,
        String description,
        String code,
        CartDeliveryMoneyResult cost,
        String deliveryMethodType,
        String deliveryEstimate,
        String estimatedDeliveryTime,
        Instant estimatedDeliveryAt,
        Boolean selected
) {

    public static CartDeliveryOptionResult from(UcpCartResponse.DeliveryOption option) {
        if (option == null) {
            return null;
        }
        return new CartDeliveryOptionResult(
                option.handle(),
                option.title(),
                option.description(),
                option.code(),
                CartDeliveryMoneyResult.from(option.cost() == null ? option.costAmount() : option.cost()),
                option.deliveryMethodType(),
                option.deliveryEstimate(),
                option.estimatedDeliveryTime(),
                option.estimatedDeliveryAt(),
                option.selected()
        );
    }
}

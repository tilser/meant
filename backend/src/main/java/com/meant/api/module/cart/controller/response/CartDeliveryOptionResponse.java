package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartDeliveryOptionResult;
import java.time.Instant;

public record CartDeliveryOptionResponse(
        String handle,
        String title,
        String description,
        String code,
        CartDeliveryMoneyResponse cost,
        String deliveryMethodType,
        String deliveryEstimate,
        String estimatedDeliveryTime,
        Instant estimatedDeliveryAt,
        Boolean selected
) {

    public static CartDeliveryOptionResponse from(CartDeliveryOptionResult result) {
        if (result == null) {
            return null;
        }
        return new CartDeliveryOptionResponse(
                result.handle(),
                result.title(),
                result.description(),
                result.code(),
                CartDeliveryMoneyResponse.from(result.cost()),
                result.deliveryMethodType(),
                result.deliveryEstimate(),
                result.estimatedDeliveryTime(),
                result.estimatedDeliveryAt(),
                result.selected()
        );
    }
}

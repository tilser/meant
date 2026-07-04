package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartDeliveryOptionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CartDeliveryOptionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String handle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CartDeliveryMoneyResponse cost,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String deliveryMethodType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String deliveryEstimate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String estimatedDeliveryTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant estimatedDeliveryAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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

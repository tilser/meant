package com.meant.api.module.cart.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CartDeliveryGroupResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String handle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CartDeliveryOptionResponse> deliveryOptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CartDeliveryOptionResponse selectedDeliveryOption
) {

    public static CartDeliveryGroupResponse from(CartDeliveryGroupResult result) {
        if (result == null) {
            return null;
        }
        return new CartDeliveryGroupResponse(
                result.id(),
                result.handle(),
                safeNonNullList(result.deliveryOptions()).stream()
                        .map(CartDeliveryOptionResponse::from)
                        .filter(option -> option != null)
                        .toList(),
                CartDeliveryOptionResponse.from(result.selectedDeliveryOption())
        );
    }
}

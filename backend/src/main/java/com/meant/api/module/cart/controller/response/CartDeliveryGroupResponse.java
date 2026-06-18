package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import java.util.List;

public record CartDeliveryGroupResponse(
        String id,
        String handle,
        List<CartDeliveryOptionResponse> deliveryOptions,
        CartDeliveryOptionResponse selectedDeliveryOption
) {

    public static CartDeliveryGroupResponse from(CartDeliveryGroupResult result) {
        return new CartDeliveryGroupResponse(
                result.id(),
                result.handle(),
                result.deliveryOptions().stream()
                        .map(CartDeliveryOptionResponse::from)
                        .toList(),
                CartDeliveryOptionResponse.from(result.selectedDeliveryOption())
        );
    }
}

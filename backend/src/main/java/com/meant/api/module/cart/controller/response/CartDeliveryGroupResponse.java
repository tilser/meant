package com.meant.api.module.cart.controller.response;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.module.cart.service.dto.CartDeliveryGroupResult;
import java.util.List;

public record CartDeliveryGroupResponse(
        String id,
        String handle,
        List<CartDeliveryOptionResponse> deliveryOptions,
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

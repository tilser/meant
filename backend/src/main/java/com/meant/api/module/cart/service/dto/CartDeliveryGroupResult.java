package com.meant.api.module.cart.service.dto;

import static com.meant.api.common.util.CollectionUtils.safeNonNullList;

import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import java.util.List;

public record CartDeliveryGroupResult(
        String id,
        String handle,
        List<CartDeliveryOptionResult> deliveryOptions,
        CartDeliveryOptionResult selectedDeliveryOption
) {

    public static CartDeliveryGroupResult from(UcpCartResponse.DeliveryGroup group) {
        if (group == null) {
            return null;
        }
        return new CartDeliveryGroupResult(
                group.id(),
                group.handle(),
                safeNonNullList(group.deliveryOptions()).stream()
                        .map(CartDeliveryOptionResult::from)
                        .filter(option -> option != null)
                        .toList(),
                CartDeliveryOptionResult.from(group.selectedDeliveryOption())
        );
    }
}

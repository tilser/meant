package com.meant.api.plugin.cart.common.dto;

public record CartDeliveryOptionSelection(
        String methodId,
        String groupId,
        String selectedOptionId
) {
}

package com.meant.api.module.cart.service.dto;

public record CartDeliveryOptionSelectionInput(
        String methodId,
        String groupId,
        String selectedOptionId
) {
}

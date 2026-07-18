package com.meant.api.module.cart.service.dto;

public record CartDeliveryAddressSelectionInput(
        String methodId,
        String id,
        Boolean selected,
        CartDeliveryAddressInput address
) {
}

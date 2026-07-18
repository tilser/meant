package com.meant.api.plugin.cart.common.dto;

public record CartDeliveryAddressSelection(
        String methodId,
        Boolean selected,
        CartDeliveryAddress address
) {
}

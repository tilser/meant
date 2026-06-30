package com.meant.api.plugin.cart.common.dto;

public record CartUpdateItem(
        String id,
        String productVariantId,
        Integer quantity
) {

    public CartUpdateItem(String id, Integer quantity) {
        this(id, null, quantity);
    }
}

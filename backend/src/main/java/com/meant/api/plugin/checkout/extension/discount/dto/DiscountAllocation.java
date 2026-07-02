package com.meant.api.plugin.checkout.extension.discount.dto;

public record DiscountAllocation(
        String path,
        Integer amount
) {
}

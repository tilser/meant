package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.ShoppingFilterResult;

public record ShoppingFilterResponse(
        String id,
        String label,
        String description,
        String category,
        String polarity,
        Integer displayOrder
) {

    public static ShoppingFilterResponse from(ShoppingFilterResult filter) {
        return new ShoppingFilterResponse(
                filter.id(),
                filter.label(),
                filter.description(),
                filter.category(),
                filter.polarity(),
                filter.displayOrder()
        );
    }
}

package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.ShoppingFilter;

public record ShoppingFilterResult(
        String id,
        String label,
        String description,
        String category,
        String polarity,
        Integer displayOrder
) {

    public static ShoppingFilterResult from(ShoppingFilter filter) {
        return new ShoppingFilterResult(
                filter.getId(),
                filter.getLabel(),
                filter.getDescription(),
                filter.getCategory(),
                filter.getPolarity(),
                filter.getDisplayOrder()
        );
    }
}

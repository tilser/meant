package com.meant.api.module.catalog.service.dto;

import java.math.BigDecimal;

public record CatalogDiscoveryRating(
        BigDecimal variantMinimum,
        Long variantMinimumCount
) {

    public CatalogDiscoveryRating {
        if (variantMinimum == null && variantMinimumCount == null) {
            throw new IllegalArgumentException("Catalog discovery rating requires a threshold");
        }
        if (variantMinimum != null
                && (variantMinimum.compareTo(BigDecimal.ZERO) < 0
                || variantMinimum.compareTo(BigDecimal.valueOf(5)) > 0)) {
            throw new IllegalArgumentException("Catalog discovery rating must be between zero and five");
        }
        if (variantMinimumCount != null && variantMinimumCount < 0) {
            throw new IllegalArgumentException("Catalog discovery rating count must not be negative");
        }
    }
}

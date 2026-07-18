package com.meant.api.module.user.service.dto;

import java.util.List;

/** Ordered batch response for canonical product references restored from Discover history. */
public record UserCanonicalProductsRehydrationResult(
        List<UserProductDetailResult> products,
        List<String> unavailableCanonicalProductKeys
) {
    public UserCanonicalProductsRehydrationResult {
        products = products == null ? List.of() : List.copyOf(products);
        unavailableCanonicalProductKeys = unavailableCanonicalProductKeys == null
                ? List.of() : List.copyOf(unavailableCanonicalProductKeys);
    }
}

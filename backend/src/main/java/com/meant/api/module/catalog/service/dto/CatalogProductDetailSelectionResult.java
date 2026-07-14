package com.meant.api.module.catalog.service.dto;

import java.util.List;

/** Provider-effective selection compared with the caller's requested variant options. */
public record CatalogProductDetailSelectionResult(
        List<ProductAttribute> requestedOptions,
        List<ProductAttribute> effectiveOptions,
        boolean complete,
        boolean relaxed,
        int matchingVariantCount
) {
    public CatalogProductDetailSelectionResult {
        requestedOptions = requestedOptions == null ? List.of() : List.copyOf(requestedOptions);
        effectiveOptions = effectiveOptions == null ? List.of() : List.copyOf(effectiveOptions);
        if (matchingVariantCount < 0) {
            throw new IllegalArgumentException("Matching variant count must not be negative");
        }
    }

    public boolean uniqueCompleteExactMatch() {
        return complete && !relaxed && matchingVariantCount == 1;
    }
}

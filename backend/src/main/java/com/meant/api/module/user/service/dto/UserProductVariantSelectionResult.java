package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;

/** Current provider detail plus the optional exact server-owned offer selected from it. */
public record UserProductVariantSelectionResult(
        RehydratedProductDetails details,
        Offer selectedOffer,
        boolean cartable
) {
    public UserProductVariantSelectionResult {
        if (details == null) {
            throw new IllegalArgumentException("Variant selection requires current product detail");
        }
        if (selectedOffer == null && cartable) {
            throw new IllegalArgumentException("A cartable selection requires an exact selected offer");
        }
    }
}

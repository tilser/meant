package com.meant.api.plugin.catalog;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import java.util.List;

public record CatalogGetProductRequest(
        String productId,
        List<ProductDetailsResponse.SelectedOption> selected,
        List<String> preferences,
        CatalogSearchContext context
) {

    public CatalogGetProductRequest(String productId, CatalogSearchContext context) {
        this(productId, null, null, context);
    }
}

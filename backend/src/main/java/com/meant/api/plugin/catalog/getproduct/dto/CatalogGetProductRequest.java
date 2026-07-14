package com.meant.api.plugin.catalog.getproduct.dto;

import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import java.util.List;

public record CatalogGetProductRequest(
        String productId,
        List<ProductDetailsResponse.SelectedOption> selected,
        List<String> preferences,
        CatalogSearchContext context,
        CatalogGetProductFilters filters
) {

    public CatalogGetProductRequest(
            String productId,
            List<ProductDetailsResponse.SelectedOption> selected,
            List<String> preferences,
            CatalogSearchContext context
    ) {
        this(productId, selected, preferences, context, null);
    }

    public CatalogGetProductRequest(String productId, CatalogSearchContext context) {
        this(productId, null, null, context, null);
    }
}

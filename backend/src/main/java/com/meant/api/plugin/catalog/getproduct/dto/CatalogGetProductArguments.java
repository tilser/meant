package com.meant.api.plugin.catalog.getproduct.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.ProductDetailsResponse;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogExtensionArguments;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogGetProductArguments(
        @JsonProperty("id")
        String id,

        List<ProductDetailsResponse.SelectedOption> selected,

        List<String> preferences,

        CatalogSearchContext context,

        Map<String, ShopifyCatalogExtensionArguments> extensions
) {
}

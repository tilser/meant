package com.meant.api.plugin.catalog.extension.shopify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ShopifyCatalogExtensionArguments(
        @JsonProperty("include_product_ids")
        boolean includeProductIds,

        @JsonProperty("include_variant_ids")
        boolean includeVariantIds,

        @JsonProperty("include_selling_plans")
        boolean includeSellingPlans,

        @JsonProperty("include_metafields")
        boolean includeMetafields
) {
}

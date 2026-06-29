package com.meant.api.plugin.catalog.shopify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.catalog.shopify.ShopifyCatalogExtensionCapability;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.Map;

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

    public static Map<String, ShopifyCatalogExtensionArguments> extensions(NegotiatedCapabilities activeCapabilities) {
        if (activeCapabilities == null || !activeCapabilities.supports(ShopifyCatalogExtensionCapability.ID)) {
            return null;
        }
        return Map.of(ShopifyCatalogExtensionCapability.ID.value(), new ShopifyCatalogExtensionArguments(
                true,
                true,
                true,
                true
        ));
    }
}

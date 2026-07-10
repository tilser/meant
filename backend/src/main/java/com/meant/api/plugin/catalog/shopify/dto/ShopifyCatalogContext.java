package com.meant.api.plugin.catalog.shopify.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyCatalogContext(
        @JsonProperty("address_country") String addressCountry,
        @JsonProperty("address_region") String addressRegion,
        @JsonProperty("postal_code") String postalCode,
        String language,
        String currency,
        String intent
) {
}

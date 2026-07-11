package com.meant.api.provider.shopify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyCatalogSelectedOption(String name, String label) {
}

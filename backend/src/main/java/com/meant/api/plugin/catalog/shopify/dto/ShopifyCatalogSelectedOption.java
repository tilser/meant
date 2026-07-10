package com.meant.api.plugin.catalog.shopify.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyCatalogSelectedOption(String name, String label) {
}

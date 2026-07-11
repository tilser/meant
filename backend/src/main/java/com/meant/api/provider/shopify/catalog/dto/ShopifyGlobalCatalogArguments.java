package com.meant.api.provider.shopify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyGlobalCatalogArguments(Catalog catalog) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            String query,
            List<String> ids,
            String id,
            List<ShopifyCatalogSelectedOption> selected,
            List<String> preferences,
            ShopifyCatalogContext context,
            ShopifyCatalogFilters filters,
            String view,
            Pagination pagination
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Pagination(
            String cursor,
            @JsonProperty("limit") int limit
    ) {
    }
}

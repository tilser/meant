package com.meant.api.plugin.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogSearchArguments(
        Catalog catalog,
        Map<String, ShopifyCatalogExtensionArguments> extensions
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            String query,
            CatalogSearchContext context,
            CatalogSearchSignals signals,
            CatalogSearchFilters filters,
            Pagination pagination
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Pagination(
            @JsonProperty("cursor")
            String cursor,
            int limit
    ) {
    }
}

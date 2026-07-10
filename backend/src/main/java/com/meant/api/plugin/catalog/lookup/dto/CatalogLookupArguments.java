package com.meant.api.plugin.catalog.lookup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogExtensionArguments;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogLookupArguments(
        Catalog catalog,

        Map<String, ShopifyCatalogExtensionArguments> extensions
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            List<String> ids,

            CatalogSearchContext context
    ) {
    }
}

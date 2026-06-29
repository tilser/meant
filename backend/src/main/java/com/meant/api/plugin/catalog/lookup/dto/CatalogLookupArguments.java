package com.meant.api.plugin.catalog.lookup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.shopify.dto.ShopifyCatalogExtensionArguments;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogLookupArguments(
        List<String> ids,

        CatalogSearchContext context,

        Map<String, ShopifyCatalogExtensionArguments> extensions
) {
}

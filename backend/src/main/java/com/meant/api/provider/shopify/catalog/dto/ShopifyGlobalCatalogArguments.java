package com.meant.api.provider.shopify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ShopifyGlobalCatalogArguments(Catalog catalog) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            String query,
            List<ShopifyCatalogItemReference> like,
            List<String> ids,
            String id,
            List<ShopifyCatalogSelectedOption> selected,
            List<String> preferences,
            ShopifyCatalogContext context,
            CatalogSearchSignals signals,
            ShopifyCatalogFilters filters,
            String view,
            Pagination pagination
    ) {

        public Catalog(
                String query,
                List<ShopifyCatalogItemReference> like,
                List<String> ids,
                String id,
                List<ShopifyCatalogSelectedOption> selected,
                List<String> preferences,
                ShopifyCatalogContext context,
                ShopifyCatalogFilters filters,
                String view,
                Pagination pagination
        ) {
            this(query, like, ids, id, selected, preferences, context, null, filters, view, pagination);
        }

        public Catalog(
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
            this(query, null, ids, id, selected, preferences, context, null, filters, view, pagination);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Pagination(
            String cursor,
            @JsonProperty("limit") int limit
    ) {
    }
}

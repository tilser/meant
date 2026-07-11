package com.meant.api.plugin.catalog.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchSignals;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogSearchArguments(
        Catalog catalog,
        Map<String, JsonNode> extensions
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

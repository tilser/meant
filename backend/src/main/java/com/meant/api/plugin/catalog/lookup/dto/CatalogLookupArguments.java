package com.meant.api.plugin.catalog.lookup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogLookupArguments(
        Catalog catalog,

        Map<String, JsonNode> extensions
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            List<String> ids,

            CatalogSearchContext context
    ) {
    }
}

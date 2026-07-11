package com.meant.api.plugin.catalog.getproduct.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchContext;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogGetProductArguments(
        Catalog catalog,

        Map<String, JsonNode> extensions
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Catalog(
            @JsonProperty("id")
            String id,

            List<ProductDetailsResponse.SelectedOption> selected,

            List<String> preferences,

            CatalogSearchContext context
    ) {
    }
}

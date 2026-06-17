package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CatalogSearchFilters(
        List<String> categories,
        CatalogSearchPriceFilter price
) {
}

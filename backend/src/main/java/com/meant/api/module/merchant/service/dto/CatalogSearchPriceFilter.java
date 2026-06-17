package com.meant.api.module.merchant.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogSearchPriceFilter(
        Long min,
        Long max
) {
}

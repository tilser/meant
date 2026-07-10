package com.meant.api.plugin.catalog.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogSearchPriceFilter(Long min, Long max) {
}

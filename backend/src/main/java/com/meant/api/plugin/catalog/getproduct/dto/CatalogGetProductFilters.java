package com.meant.api.plugin.catalog.getproduct.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Minimal get-product filters needed to request the complete option/variant surface. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CatalogGetProductFilters(
        Boolean available
) {
}

package com.meant.api.module.catalog.service.dto;

import java.util.List;

/**
 * Provider-neutral hard discovery constraints. Concrete providers translate the supported subset to their wire DTO.
 */
public record CatalogDiscoveryFilters(
        Boolean available,
        List<CatalogDiscoveryCondition> conditions,
        CatalogDiscoveryLocation shipsTo,
        List<CatalogDiscoveryLocation> shipsFrom,
        CatalogDiscoveryPrice price,
        List<String> shopIds,
        List<String> categoryIds,
        List<CatalogDiscoveryAttributeFilter> attributes,
        CatalogDiscoveryRating rating,
        List<CatalogDiscoveryPriceTier> priceTiers
) {

    public CatalogDiscoveryFilters {
        conditions = immutable(conditions);
        shipsFrom = immutable(shipsFrom);
        shopIds = cleanValues(shopIds);
        categoryIds = cleanValues(categoryIds);
        attributes = immutable(attributes);
        priceTiers = immutable(priceTiers);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : values.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private static List<String> cleanValues(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}

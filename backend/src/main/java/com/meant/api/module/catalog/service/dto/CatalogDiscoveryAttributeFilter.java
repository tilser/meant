package com.meant.api.module.catalog.service.dto;

import java.util.List;

public record CatalogDiscoveryAttributeFilter(
        CatalogDiscoveryAttributeName name,
        List<String> values
) {

    public CatalogDiscoveryAttributeFilter {
        if (name == null) {
            throw new IllegalArgumentException("Catalog discovery attribute name is required");
        }
        values = values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Catalog discovery attribute values are required");
        }
    }
}

package com.meant.api.module.catalog.service.dto;

import java.util.List;

/** Optional get-product selection hints supplied against a server-owned anchor offer. */
public record CatalogProductDetailSelection(
        List<ProductAttribute> selectedOptions,
        List<String> preferences
) {
    public CatalogProductDetailSelection {
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        preferences = preferences == null
                ? List.of()
                : preferences.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
    }
}

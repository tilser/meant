package com.meant.api.plugin.catalog.common.dto;

public record CatalogSourcePage(
        String cursor,
        boolean hasNextPage,
        Long estimatedTotalCount
) {

    public CatalogSourcePage {
        cursor = cursor == null || cursor.isBlank() ? null : cursor.trim();
        if (estimatedTotalCount != null && estimatedTotalCount < 0) {
            throw new IllegalArgumentException("Estimated total count must not be negative");
        }
    }
}

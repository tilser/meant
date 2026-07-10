package com.meant.api.plugin.catalog.common.dto;

import java.net.URI;

public record ProductMedia(
        ProductMediaType type,
        URI url,
        String altText,
        Integer width,
        Integer height
) {

    public ProductMedia {
        if (type == null || url == null) {
            throw new IllegalArgumentException("Product media type and URL must not be null");
        }
        altText = trimToNull(altText);
        if (width != null && width <= 0 || height != null && height <= 0) {
            throw new IllegalArgumentException("Product media dimensions must be positive");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.meant.api.plugin.catalog.common.dto;

import java.net.URI;

public record ProductAttribution(
        String label,
        URI url,
        ResultSourceReference sourceReference
) {

    public ProductAttribution {
        if (label == null || label.isBlank() || sourceReference == null) {
            throw new IllegalArgumentException("Attribution label and source must not be blank");
        }
        label = label.trim();
    }
}

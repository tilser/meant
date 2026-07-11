package com.meant.api.module.catalog.service.dto;

import java.net.URI;

/** Typed debugging reference to the discovery path that produced an observation. */
public record ResultSourceReference(
        ResultSourceType type,
        String reference,
        URI uri
) {

    public ResultSourceReference {
        if (type == null) {
            throw new IllegalArgumentException("Result source type must not be null");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Result source reference must not be blank");
        }
        reference = reference.trim();
    }
}

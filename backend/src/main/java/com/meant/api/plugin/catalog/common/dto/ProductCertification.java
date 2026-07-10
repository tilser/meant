package com.meant.api.plugin.catalog.common.dto;

import java.net.URI;

public record ProductCertification(
        String name,
        String issuer,
        String identifier,
        URI verificationUrl
) {

    public ProductCertification {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Certification name must not be blank");
        }
        name = name.trim();
        issuer = trimToNull(issuer);
        identifier = trimToNull(identifier);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

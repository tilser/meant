package com.meant.api.module.catalog.service.dto;

import java.util.Locale;

public record CatalogDiscoveryLocation(
        String country,
        String region,
        String postalCode
) {

    public CatalogDiscoveryLocation {
        if (country == null || !country.trim().matches("(?i)[A-Z]{2}")) {
            throw new IllegalArgumentException("Catalog discovery country must be an ISO alpha-2 code");
        }
        country = country.trim().toUpperCase(Locale.ROOT);
        region = clean(region);
        postalCode = clean(postalCode);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package com.meant.api.module.catalog.service.dto;

import java.util.Locale;

/** Provider-neutral identity of the commerce system that issued an external reference. */
public record ProviderIdentity(String value) {

    public ProviderIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Provider identity must not be blank");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
    }
}

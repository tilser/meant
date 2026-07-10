package com.meant.api.plugin.catalog.common.dto;

/** Stable identity of the provider catalog, storefront, cache, or other path that observed a result. */
public record DiscoverySourceIdentity(
        ProviderIdentity provider,
        ResultSourceType type,
        String value
) {

    public DiscoverySourceIdentity {
        if (provider == null || type == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("Discovery source provider, type, and value are required");
        }
        value = value.trim();
    }
}

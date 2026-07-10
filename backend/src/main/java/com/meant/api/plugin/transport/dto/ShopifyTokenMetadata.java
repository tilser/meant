package com.meant.api.plugin.transport.dto;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record ShopifyTokenMetadata(
        Instant expiresAt,
        Set<String> scopes,
        ShopifyTokenLimits limits
) {

    public ShopifyTokenMetadata {
        scopes = scopes == null ? Set.of() : Set.copyOf(new TreeSet<>(scopes));
        limits = limits == null ? ShopifyTokenLimits.none() : limits;
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}

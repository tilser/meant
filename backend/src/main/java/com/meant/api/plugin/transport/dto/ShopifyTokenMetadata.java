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
        if (scopes == null) {
            scopes = Set.of();
        } else {
            Set<String> clean = new TreeSet<>();
            for (String scope : scopes) {
                if (scope != null) {
                    clean.add(scope);
                }
            }
            scopes = Set.copyOf(clean);
        }
        limits = limits == null ? ShopifyTokenLimits.none() : limits;
    }

    public boolean hasScope(String scope) {
        return scope != null && scopes.contains(scope);
    }
}

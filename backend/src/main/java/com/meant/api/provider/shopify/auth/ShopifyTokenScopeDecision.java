package com.meant.api.provider.shopify.auth;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record ShopifyTokenScopeDecision(
        Availability availability,
        Set<String> requiredScopes,
        Set<String> missingScopes,
        Optional<ShopifyTokenMetadata> metadata
) {

    public ShopifyTokenScopeDecision {
        availability = Objects.requireNonNull(availability, "availability");
        requiredScopes = immutable(requiredScopes);
        missingScopes = immutable(missingScopes);
        metadata = metadata == null ? Optional.empty() : metadata;
    }

    public boolean available() {
        return availability == Availability.AVAILABLE;
    }

    public enum Availability {
        AVAILABLE,
        DISABLED,
        MISSING_SCOPES,
        UNAUTHORIZED_REFRESH_ALREADY_ATTEMPTED
    }

    private static Set<String> immutable(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> clean = new TreeSet<>();
        for (String value : values) {
            if (value != null) {
                clean.add(value);
            }
        }
        return Collections.unmodifiableSet(clean);
    }
}

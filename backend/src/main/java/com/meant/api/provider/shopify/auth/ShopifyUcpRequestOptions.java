package com.meant.api.provider.shopify.auth;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public record ShopifyUcpRequestOptions(
        URI endpoint,
        Set<String> allowedHosts,
        Set<String> requiredScopes,
        Duration connectTimeout,
        Duration readTimeout,
        Duration requestDeadline
) {

    public ShopifyUcpRequestOptions {
        if (endpoint == null || !endpoint.isAbsolute() || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Shopify UCP endpoint must be an absolute URI with a host");
        }
        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("Shopify UCP endpoint must use HTTPS");
        }
        if (endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Shopify UCP endpoint must not contain user info or a fragment");
        }
        allowedHosts = normalized(allowedHosts, true);
        requiredScopes = normalized(requiredScopes, false);
        if (!allowedHosts.contains(endpoint.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Shopify UCP endpoint host is not allowlisted");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        requirePositive(requestDeadline, "requestDeadline");
    }

    private static Set<String> normalized(Set<String> values, boolean lowercase) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Configured Shopify hosts and scopes must not be empty");
        }
        Set<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Configured Shopify hosts and scopes must not contain blanks");
            }
            String clean = value.trim();
            normalized.add(lowercase ? clean.toLowerCase(Locale.ROOT) : clean);
        }
        return Set.copyOf(normalized);
    }

    private static void requirePositive(Duration duration, String field) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}

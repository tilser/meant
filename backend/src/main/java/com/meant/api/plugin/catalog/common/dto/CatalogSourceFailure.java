package com.meant.api.plugin.catalog.common.dto;

import java.time.Duration;

/** Safe, provider-scoped failure data that federation can handle without failing other sources. */
public record CatalogSourceFailure(
        CatalogSourceFailureKind kind,
        String message,
        Duration retryAfter,
        Integer upstreamStatus
) {

    public CatalogSourceFailure {
        if (kind == null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Catalog source failure kind and message are required");
        }
        message = message.trim();
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("Retry delay must not be negative");
        }
        if (upstreamStatus != null && (upstreamStatus < 100 || upstreamStatus > 599)) {
            throw new IllegalArgumentException("Upstream status must be a valid HTTP status");
        }
    }
}

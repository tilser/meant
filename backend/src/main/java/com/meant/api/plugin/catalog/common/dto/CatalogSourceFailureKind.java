package com.meant.api.plugin.catalog.common.dto;

public enum CatalogSourceFailureKind {
    AUTHENTICATION,
    INVALID_REQUEST,
    RATE_LIMITED,
    TIMEOUT,
    TRANSIENT_UPSTREAM,
    UNAVAILABLE,
    MALFORMED_RESPONSE
}

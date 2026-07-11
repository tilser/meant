package com.meant.api.plugin.catalog.common.dto;

public enum CatalogRehydrationFailureKind {
    NO_PROVIDER,
    AMBIGUOUS_PROVIDER,
    CAPABILITY_UNAVAILABLE,
    NOT_FOUND,
    INVALID_RESPONSE,
    UPSTREAM_UNAVAILABLE
}

package com.meant.api.module.catalog.service.dto;

public enum CatalogRehydrationFailureKind {
    NO_PROVIDER,
    AMBIGUOUS_PROVIDER,
    CAPABILITY_UNAVAILABLE,
    INVALID_REFERENCE,
    NOT_FOUND,
    INVALID_RESPONSE,
    UPSTREAM_UNAVAILABLE
}

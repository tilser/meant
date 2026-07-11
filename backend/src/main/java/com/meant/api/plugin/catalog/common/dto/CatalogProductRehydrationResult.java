package com.meant.api.plugin.catalog.common.dto;

/** Typed result that never treats persisted display hints as authoritative on failure. */
public record CatalogProductRehydrationResult(
        CatalogProductReference reference,
        CatalogRehydrationStatus status,
        RehydratedCommercialFacts facts,
        CatalogRehydrationFailureKind failure
) {
    public CatalogProductRehydrationResult {
        if (reference == null || status == null) {
            throw new IllegalArgumentException("Rehydration reference and status are required");
        }
        if ((status == CatalogRehydrationStatus.FRESH) != (facts != null)) {
            throw new IllegalArgumentException("Only fresh rehydration results may contain authoritative facts");
        }
        if ((status == CatalogRehydrationStatus.FRESH) == (failure != null)) {
            throw new IllegalArgumentException("Failed rehydration results require a typed failure");
        }
    }

    public static CatalogProductRehydrationResult fresh(
            CatalogProductReference reference,
            RehydratedCommercialFacts facts
    ) {
        return new CatalogProductRehydrationResult(reference, CatalogRehydrationStatus.FRESH, facts, null);
    }

    public static CatalogProductRehydrationResult failed(
            CatalogProductReference reference,
            CatalogRehydrationStatus status,
            CatalogRehydrationFailureKind failure
    ) {
        return new CatalogProductRehydrationResult(reference, status, null, failure);
    }
}

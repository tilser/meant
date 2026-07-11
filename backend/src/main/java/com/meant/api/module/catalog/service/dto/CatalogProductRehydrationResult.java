package com.meant.api.module.catalog.service.dto;

/** Typed result that never treats persisted display hints as authoritative on failure. */
public record CatalogProductRehydrationResult(
        CatalogProductReference reference,
        CatalogProductReference resolvedReference,
        CatalogRehydrationStatus status,
        RehydratedCommercialFacts facts,
        CatalogRehydrationFailureKind failure
) {
    public CatalogProductRehydrationResult {
        if (reference == null || status == null) {
            throw new IllegalArgumentException("Rehydration reference and status are required");
        }
        if ((status == CatalogRehydrationStatus.FRESH) != (facts != null && resolvedReference != null)) {
            throw new IllegalArgumentException("Fresh results require authoritative facts and a verified reference");
        }
        if ((status == CatalogRehydrationStatus.FRESH) == (failure != null)) {
            throw new IllegalArgumentException("Failed rehydration results require a typed failure");
        }
    }

    public static CatalogProductRehydrationResult fresh(
            CatalogProductReference requestedReference,
            CatalogProductReference resolvedReference,
            RehydratedCommercialFacts facts
    ) {
        return new CatalogProductRehydrationResult(
                requestedReference,
                resolvedReference,
                CatalogRehydrationStatus.FRESH,
                facts,
                null
        );
    }

    public static CatalogProductRehydrationResult failed(
            CatalogProductReference reference,
            CatalogRehydrationStatus status,
            CatalogRehydrationFailureKind failure
    ) {
        return new CatalogProductRehydrationResult(reference, null, status, null, failure);
    }
}

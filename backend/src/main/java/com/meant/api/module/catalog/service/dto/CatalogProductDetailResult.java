package com.meant.api.module.catalog.service.dto;

/** Full transient detail paired with the exact commercial observation made by the same provider call. */
public record CatalogProductDetailResult(
        CatalogProductRehydrationResult rehydration,
        RehydratedProductDetails details,
        CatalogProductDetailSelectionResult selection
) {
    public CatalogProductDetailResult(
            CatalogProductRehydrationResult rehydration,
            RehydratedProductDetails details
    ) {
        this(rehydration, details, null);
    }

    public CatalogProductDetailResult {
        if (rehydration == null) {
            throw new IllegalArgumentException("Product detail requires a rehydration result");
        }
        if ((rehydration.status() == CatalogRehydrationStatus.FRESH) != (details != null)) {
            throw new IllegalArgumentException("Fresh product detail requires a transient detail projection");
        }
    }

    public static CatalogProductDetailResult from(
            CatalogProductRehydrationResult rehydration,
            RehydratedProductDetails details
    ) {
        return new CatalogProductDetailResult(rehydration, details, null);
    }

    public CatalogProductDetailResult withSelection(CatalogProductDetailSelectionResult value) {
        return new CatalogProductDetailResult(rehydration, details, value);
    }

    public static CatalogProductDetailResult failed(
            CatalogProductReference reference,
            CatalogRehydrationStatus status,
            CatalogRehydrationFailureKind failure
    ) {
        return new CatalogProductDetailResult(
                CatalogProductRehydrationResult.failed(reference, status, failure),
                null,
                null
        );
    }
}

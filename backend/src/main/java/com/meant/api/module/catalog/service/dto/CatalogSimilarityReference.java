package com.meant.api.module.catalog.service.dto;

/** Provider-neutral product reference used to narrow catalog discovery by similarity. */
public record CatalogSimilarityReference(
        ProviderIdentity provider,
        ExternalIdentifier productReference
) {

    public CatalogSimilarityReference {
        if (provider == null || productReference == null) {
            throw new IllegalArgumentException("Similarity provider and product reference are required");
        }
        if (productReference.type() != ExternalIdentifierType.PRODUCT
                || !provider.value().equals(productReference.namespace())) {
            throw new IllegalArgumentException(
                    "Similarity product reference must use the provider namespace");
        }
    }
}

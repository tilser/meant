package com.meant.api.plugin.catalog.common.dto;

import java.util.List;
import java.util.UUID;

/** Durable provider identifiers required to rehydrate a saved or selected product. */
public record CatalogProductReference(
        String interactionKey,
        DiscoverySourceIdentity discoverySource,
        UUID localMerchantId,
        LocalMerchantRouting localRouting,
        ExternalIdentifier externalMerchantReference,
        ExternalIdentifier externalProductReference,
        ExternalIdentifier externalVariantReference,
        List<ProductAttribute> selectedOptions
) {

    public CatalogProductReference {
        if (interactionKey == null || interactionKey.isBlank()
                || discoverySource == null || externalProductReference == null) {
            throw new IllegalArgumentException("Interaction key, discovery source, and product reference are required");
        }
        interactionKey = interactionKey.trim();
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        requireProviderReference(externalMerchantReference, ExternalIdentifierType.MERCHANT, discoverySource, false);
        requireProviderReference(externalProductReference, ExternalIdentifierType.PRODUCT, discoverySource, true);
        requireProviderReference(externalVariantReference, ExternalIdentifierType.VARIANT, discoverySource, false);
    }

    public static CatalogProductReference from(String interactionKey, ResultProvenance provenance) {
        return new CatalogProductReference(
                interactionKey,
                provenance.discoverySource(),
                null,
                provenance.localRouting(),
                provenance.externalMerchantReference(),
                provenance.externalProductReference(),
                provenance.externalVariantReference(),
                List.of()
        );
    }

    private static void requireProviderReference(
            ExternalIdentifier reference,
            ExternalIdentifierType type,
            DiscoverySourceIdentity source,
            boolean required
    ) {
        if (reference == null) {
            if (required) {
                throw new IllegalArgumentException(type + " reference is required");
            }
            return;
        }
        if (reference.type() != type || !source.provider().value().equals(reference.namespace())) {
            throw new IllegalArgumentException(type + " reference must use the discovery provider namespace");
        }
    }
}

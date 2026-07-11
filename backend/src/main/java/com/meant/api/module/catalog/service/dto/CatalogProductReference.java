package com.meant.api.module.catalog.service.dto;

import java.util.List;
import java.util.UUID;
import java.util.Comparator;

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
    private static final Comparator<ProductAttribute> OPTION_ORDER = Comparator
            .comparing((ProductAttribute option) -> option.group() == null ? "" : option.group())
            .thenComparing(ProductAttribute::name)
            .thenComparing(ProductAttribute::value);

    public CatalogProductReference {
        if (interactionKey == null || interactionKey.isBlank()
                || discoverySource == null || externalProductReference == null) {
            throw new IllegalArgumentException("Interaction key, discovery source, and product reference are required");
        }
        interactionKey = interactionKey.trim();
        selectedOptions = selectedOptions == null
                ? List.of()
                : selectedOptions.stream().distinct().sorted(OPTION_ORDER).toList();
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

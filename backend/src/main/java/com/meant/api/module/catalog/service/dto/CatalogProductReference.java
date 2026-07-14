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
        String externalMerchantDomain,
        ExternalIdentifier externalProductReference,
        ExternalIdentifier externalVariantReference,
        List<ProductAttribute> selectedOptions,
        List<OfferComponentIdentity> components,
        SellingPlanIdentity sellingPlanIdentity
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
        components = components == null
                ? List.of()
                : components.stream().sorted(OfferComponentIdentity::compareCanonical).toList();
        requireProviderReference(externalMerchantReference, ExternalIdentifierType.MERCHANT, discoverySource, false);
        requireProviderReference(externalProductReference, ExternalIdentifierType.PRODUCT, discoverySource, true);
        requireProviderReference(externalVariantReference, ExternalIdentifierType.VARIANT, discoverySource, false);
        for (OfferComponentIdentity component : components) {
            requireProviderReference(
                    component.externalProductIdentity(), ExternalIdentifierType.PRODUCT, discoverySource, true);
            requireProviderReference(
                    component.externalVariantIdentity(), ExternalIdentifierType.VARIANT, discoverySource, false);
        }
        if (sellingPlanIdentity != null) {
            requireProviderReference(
                    sellingPlanIdentity.groupReference(),
                    ExternalIdentifierType.SELLING_PLAN_GROUP,
                    discoverySource,
                    false
            );
            requireProviderReference(
                    sellingPlanIdentity.planReference(),
                    ExternalIdentifierType.SELLING_PLAN,
                    discoverySource,
                    false
            );
        }
        externalMerchantDomain = externalMerchantDomain == null
                ? null : externalMerchantDomain.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public CatalogProductReference(
            String interactionKey,
            DiscoverySourceIdentity discoverySource,
            UUID localMerchantId,
            LocalMerchantRouting localRouting,
            ExternalIdentifier externalMerchantReference,
            String externalMerchantDomain,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            List<ProductAttribute> selectedOptions
    ) {
        this(interactionKey, discoverySource, localMerchantId, localRouting, externalMerchantReference,
                externalMerchantDomain, externalProductReference, externalVariantReference, selectedOptions,
                List.of(), null);
    }

    public CatalogProductReference(
            String interactionKey,
            DiscoverySourceIdentity discoverySource,
            UUID localMerchantId,
            LocalMerchantRouting localRouting,
            ExternalIdentifier externalMerchantReference,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            List<ProductAttribute> selectedOptions
    ) {
        this(interactionKey, discoverySource, localMerchantId, localRouting, externalMerchantReference, null,
                externalProductReference, externalVariantReference, selectedOptions, List.of(), null);
    }

    public static CatalogProductReference from(String interactionKey, ResultProvenance provenance) {
        return new CatalogProductReference(
                interactionKey,
                provenance.discoverySource(),
                null,
                provenance.localRouting(),
                provenance.externalMerchantReference(),
                provenance.externalMerchantDomain(),
                provenance.externalProductReference(),
                provenance.externalVariantReference(),
                List.of(),
                List.of(),
                null
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

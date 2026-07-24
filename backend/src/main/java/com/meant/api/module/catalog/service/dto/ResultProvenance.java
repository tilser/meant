package com.meant.api.module.catalog.service.dto;

/** Provider evidence, discovery-source identity, optional local routing, and freshness for an observation. */
public record ResultProvenance(
        ProviderIdentity provider,
        DiscoverySourceIdentity discoverySource,
        LocalMerchantRouting localRouting,
        ExternalIdentifier externalMerchantReference,
        String externalMerchantDomain,
        ExternalIdentifier externalProductReference,
        ExternalIdentifier externalVariantReference,
        ResultFreshness freshness,
        ResultSourceReference sourceReference,
        String merchantOrigin
) {

    public ResultProvenance {
        if (provider == null
                || discoverySource == null
                || externalProductReference == null
                || freshness == null
                || sourceReference == null) {
            throw new IllegalArgumentException("Required provenance identity and freshness must not be null");
        }
        if (!provider.equals(discoverySource.provider())) {
            throw new IllegalArgumentException("Provenance provider must match its discovery source provider");
        }
        if (sourceReference.type() != discoverySource.type()) {
            throw new IllegalArgumentException("Debug source type must match discovery source type");
        }
        requireProviderIdentifier(externalMerchantReference, ExternalIdentifierType.MERCHANT, provider, false);
        requireProviderIdentifier(externalProductReference, ExternalIdentifierType.PRODUCT, provider, true);
        requireProviderIdentifier(externalVariantReference, ExternalIdentifierType.VARIANT, provider, false);
        externalMerchantDomain = normalizeDomain(externalMerchantDomain);
        merchantOrigin = normalizeDomain(merchantOrigin);
    }

    public ResultProvenance(
            ProviderIdentity provider,
            DiscoverySourceIdentity discoverySource,
            LocalMerchantRouting localRouting,
            ExternalIdentifier externalMerchantReference,
            String externalMerchantDomain,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            ResultFreshness freshness,
            ResultSourceReference sourceReference
    ) {
        this(
                provider,
                discoverySource,
                localRouting,
                externalMerchantReference,
                externalMerchantDomain,
                externalProductReference,
                externalVariantReference,
                freshness,
                sourceReference,
                null
        );
    }

    public ResultProvenance(
            ProviderIdentity provider,
            DiscoverySourceIdentity discoverySource,
            LocalMerchantRouting localRouting,
            ExternalIdentifier externalMerchantReference,
            ExternalIdentifier externalProductReference,
            ExternalIdentifier externalVariantReference,
            ResultFreshness freshness,
            ResultSourceReference sourceReference
    ) {
        this(provider, discoverySource, localRouting, externalMerchantReference, null,
                externalProductReference, externalVariantReference, freshness, sourceReference, null);
    }

    public ResultProvenance withMerchantOrigin(String verifiedMerchantOrigin) {
        return new ResultProvenance(
                provider,
                discoverySource,
                localRouting,
                externalMerchantReference,
                externalMerchantDomain,
                externalProductReference,
                externalVariantReference,
                freshness,
                sourceReference,
                verifiedMerchantOrigin
        );
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return null;
        }
        String normalized = domain.trim().toLowerCase(java.util.Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static void requireProviderIdentifier(
            ExternalIdentifier identifier,
            ExternalIdentifierType expectedType,
            ProviderIdentity provider,
            boolean required
    ) {
        if (identifier == null) {
            if (required) {
                throw new IllegalArgumentException(expectedType + " provenance reference is required");
            }
            return;
        }
        if (identifier.type() != expectedType || !provider.value().equals(identifier.namespace())) {
            throw new IllegalArgumentException(
                    "%s provenance reference must use its expected type and provider namespace".formatted(expectedType));
        }
    }
}

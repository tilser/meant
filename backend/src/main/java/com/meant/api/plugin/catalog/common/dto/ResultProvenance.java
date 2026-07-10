package com.meant.api.plugin.catalog.common.dto;

/** Provider evidence, discovery-source identity, optional local routing, and freshness for an observation. */
public record ResultProvenance(
        ProviderIdentity provider,
        DiscoverySourceIdentity discoverySource,
        LocalMerchantRouting localRouting,
        ExternalIdentifier externalMerchantReference,
        ExternalIdentifier externalProductReference,
        ExternalIdentifier externalVariantReference,
        ResultFreshness freshness,
        ResultSourceReference sourceReference
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

package com.meant.api.plugin.catalog.common.dto;

import java.util.UUID;

/**
 * Stable seller scope for offer identity.
 *
 * <p>A provider-namespaced external merchant identity is authoritative when available. The local
 * MerchantIntegration fallback exists only for providers that expose no stable merchant identity;
 * it is identity, not a discovery-source or routing reference.
 */
public record OfferMerchantScope(
        ExternalIdentifier externalMerchantIdentity,
        UUID merchantIntegrationFallbackId
) {

    public OfferMerchantScope {
        if ((externalMerchantIdentity == null) == (merchantIntegrationFallbackId == null)) {
            throw new IllegalArgumentException("Merchant scope needs exactly one identity authority");
        }
        if (externalMerchantIdentity != null
                && externalMerchantIdentity.type() != ExternalIdentifierType.MERCHANT) {
            throw new IllegalArgumentException("External merchant scope must use a MERCHANT identifier");
        }
    }

    public static OfferMerchantScope external(ExternalIdentifier externalMerchantIdentity) {
        return new OfferMerchantScope(externalMerchantIdentity, null);
    }

    public static OfferMerchantScope localIntegrationFallback(UUID merchantIntegrationId) {
        return new OfferMerchantScope(null, merchantIntegrationId);
    }

    public OfferMerchantScopeType type() {
        return externalMerchantIdentity == null
                ? OfferMerchantScopeType.LOCAL_MERCHANT_INTEGRATION_FALLBACK
                : OfferMerchantScopeType.EXTERNAL_MERCHANT;
    }
}

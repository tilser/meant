package com.meant.api.plugin.catalog.common.dto;

import java.util.UUID;

/** Complete source identity for a product or offer observation. */
public record ResultProvenance(
        ProviderIdentity provider,
        UUID merchantIntegrationId,
        ExternalIdentifier externalMerchantReference,
        ExternalIdentifier externalProductReference,
        ExternalIdentifier externalVariantReference,
        ResultFreshness freshness,
        ResultSourceReference sourceReference
) {

    public ResultProvenance {
        if (provider == null
                || merchantIntegrationId == null
                || externalMerchantReference == null
                || externalProductReference == null
                || freshness == null
                || sourceReference == null) {
            throw new IllegalArgumentException("Required provenance identity and freshness must not be null");
        }
    }
}

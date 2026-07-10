package com.meant.api.plugin.catalog.common.dto;

import com.meant.api.plugin.catalog.common.support.CanonicalCommerceKey;
import java.util.UUID;

/** Stable provider identity for a merchant/product/variant/selling-plan offer. */
public record OfferIdentity(
        ProviderIdentity provider,
        UUID merchantIntegrationId,
        ExternalIdentifier externalMerchantIdentity,
        ExternalIdentifier externalProductIdentity,
        ExternalIdentifier externalVariantIdentity,
        SellingPlanIdentity sellingPlanIdentity
) {

    public OfferIdentity {
        if (provider == null
                || merchantIntegrationId == null
                || externalMerchantIdentity == null
                || externalProductIdentity == null) {
            throw new IllegalArgumentException("Required offer identity fields must not be null");
        }
    }

    public String key() {
        return CanonicalCommerceKey.offerKey(this);
    }
}

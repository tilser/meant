package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.support.OfferIdentityStrategy;
import org.springframework.stereotype.Component;

/** Aligns Storefront product GIDs with Global Catalog UPID offers through their shared variant GID. */
@Component
public class ShopifyOfferIdentityStrategy implements OfferIdentityStrategy {

    public static final ProviderIdentity PROVIDER = new ProviderIdentity("SHOPIFY");
    private static final String VARIANT_PRODUCT_ANCHOR = "variant-product:v1:";

    @Override
    public boolean supports(ProviderIdentity provider) {
        return PROVIDER.equals(provider);
    }

    @Override
    public ExternalIdentifier product(
            ProviderIdentity provider,
            ExternalIdentifier externalProductIdentity,
            ExternalIdentifier externalVariantIdentity
    ) {
        return productIdentity(provider, externalProductIdentity, externalVariantIdentity);
    }

    static ExternalIdentifier productIdentity(
            ProviderIdentity provider,
            ExternalIdentifier externalProductIdentity,
            ExternalIdentifier externalVariantIdentity
    ) {
        if (provider == null || externalProductIdentity == null) {
            throw new IllegalArgumentException("Provider and external product identity are required");
        }
        if (externalVariantIdentity == null) {
            return externalProductIdentity;
        }
        return new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT,
                provider.value(),
                VARIANT_PRODUCT_ANCHOR + externalVariantIdentity.value()
        );
    }
}

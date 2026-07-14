package com.meant.api.provider.shopify.catalog;

import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.port.CatalogPurchaseReferencePolicy;
import org.springframework.stereotype.Component;

/** Requires the verified storefront domain needed to route an external-only Shopify offer. */
@Component
public class ShopifyCatalogPurchaseReferencePolicy implements CatalogPurchaseReferencePolicy {

    @Override
    public boolean supports(DiscoverySourceIdentity source) {
        return source != null && ShopifyOfferIdentityStrategy.PROVIDER.equals(source.provider());
    }

    @Override
    public boolean allows(CatalogProductReference reference) {
        boolean locallyRouted = reference.localMerchantId() != null || reference.localRouting() != null;
        boolean externallyRouted = reference.externalMerchantReference() != null;
        return !externallyRouted
                || locallyRouted
                || reference.externalMerchantDomain() != null
                && !reference.externalMerchantDomain().isBlank();
    }
}

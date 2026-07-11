package com.meant.api.module.merchant.constant;

import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.plugin.catalog.common.dto.ProviderIdentity;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;

/** Server-owned identity of the direct merchant storefront discovery source. */
public final class MerchantCatalogSourceIdentity {
    public static final ProviderIdentity PROVIDER = new ProviderIdentity(MerchantIntegrationProvider.GENERIC_UCP.name());
    public static final DiscoverySourceIdentity DISCOVERY_SOURCE = new DiscoverySourceIdentity(
            PROVIDER,
            ResultSourceType.MERCHANT_STOREFRONT,
            "MEANT_MERCHANT_SEMANTIC"
    );

    private MerchantCatalogSourceIdentity() {
    }
}

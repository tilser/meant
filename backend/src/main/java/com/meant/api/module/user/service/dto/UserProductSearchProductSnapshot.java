package com.meant.api.module.user.service.dto;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.plugin.catalog.common.dto.DiscoverySourceIdentity;
import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import jakarta.validation.constraints.NotNull;

public record UserProductSearchProductSnapshot(
        String productKey,
        String productHash,
        @NotNull MerchantSemanticProductResult product,
        @NotNull DiscoverySourceIdentity discoverySource
) {
    public UserProductSearchProductSnapshot(
            String productKey,
            String productHash,
            MerchantSemanticProductResult product
    ) {
        this(productKey, productHash, product, MerchantCatalogSourceIdentity.DISCOVERY_SOURCE);
    }
}

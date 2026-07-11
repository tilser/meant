package com.meant.api.module.cart.service.dto;

import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.UUID;

/** Immutable execution scope for exactly one provider seller/integration remote cart. */
public record CartRoutingTarget(
        String scopeKey,
        MerchantIntegrationProvider provider,
        UUID merchantIntegrationId,
        String externalMerchantId,
        MerchantCartProvider merchantProvider
) {
}

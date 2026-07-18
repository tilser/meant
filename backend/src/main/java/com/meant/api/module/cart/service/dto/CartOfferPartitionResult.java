package com.meant.api.module.cart.service.dto;

import java.util.List;
import java.util.UUID;

/** One immutable merchant/provider execution scope produced from exact selected offers. */
public record CartOfferPartitionResult(
        String routingScopeKey,
        String provider,
        UUID merchantIntegrationId,
        String externalMerchantId,
        UUID merchantId,
        String merchantDomain,
        List<Item> items
) {

    public CartOfferPartitionResult {
        items = List.copyOf(items);
    }

    public record Item(String offerKey, int quantity) {
    }
}

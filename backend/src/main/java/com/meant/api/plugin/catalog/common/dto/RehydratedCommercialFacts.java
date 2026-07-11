package com.meant.api.plugin.catalog.common.dto;

import java.util.List;

/** Current commercial facts from a lookup/get-product response; never reconstructed from a saved snapshot. */
public record RehydratedCommercialFacts(
        String title,
        Money price,
        OfferAvailability availability,
        ExternalIdentifier selectedVariant,
        List<ProductAttribute> selectedOptions,
        List<OfferDelivery> fulfillment,
        List<ProductMedia> sourceMedia,
        ResultFreshness freshness,
        CommercialFactsFreshness purchaseFreshness
) {
    public RehydratedCommercialFacts {
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        fulfillment = fulfillment == null ? List.of() : List.copyOf(fulfillment);
        sourceMedia = sourceMedia == null ? List.of() : List.copyOf(sourceMedia);
        if (freshness == null || purchaseFreshness == null) {
            throw new IllegalArgumentException("Rehydrated commercial facts require current freshness");
        }
    }
}

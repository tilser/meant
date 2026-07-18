package com.meant.api.module.catalog.service.dto;

import java.net.URI;
import java.util.List;

/** Current commercial facts from a lookup/get-product response; never reconstructed from a saved snapshot. */
public record RehydratedCommercialFacts(
        String title,
        String merchantName,
        URI productUrl,
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
        availability = availability == null ? OfferAvailability.unknown() : availability;
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
        fulfillment = fulfillment == null ? List.of() : List.copyOf(fulfillment);
        sourceMedia = sourceMedia == null ? List.of() : List.copyOf(sourceMedia);
        if (freshness == null || purchaseFreshness == null) {
            throw new IllegalArgumentException("Rehydrated commercial facts require current freshness");
        }
    }

    public RehydratedCommercialFacts(
            String title,
            String merchantName,
            Money price,
            OfferAvailability availability,
            ExternalIdentifier selectedVariant,
            List<ProductAttribute> selectedOptions,
            List<OfferDelivery> fulfillment,
            List<ProductMedia> sourceMedia,
            ResultFreshness freshness,
            CommercialFactsFreshness purchaseFreshness
    ) {
        this(
                title,
                merchantName,
                null,
                price,
                availability,
                selectedVariant,
                selectedOptions,
                fulfillment,
                sourceMedia,
                freshness,
                purchaseFreshness
        );
    }

    public RehydratedCommercialFacts(
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
        this(
                title,
                null,
                null,
                price,
                availability,
                selectedVariant,
                selectedOptions,
                fulfillment,
                sourceMedia,
                freshness,
                purchaseFreshness
        );
    }
}

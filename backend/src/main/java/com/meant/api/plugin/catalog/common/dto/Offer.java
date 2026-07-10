package com.meant.api.plugin.catalog.common.dto;

import java.net.URI;
import java.util.List;

public record Offer(
        OfferIdentity identity,
        String merchantName,
        String variantTitle,
        Money price,
        Money listPrice,
        OfferAvailability availability,
        List<OfferDelivery> delivery,
        URI checkoutUrl,
        List<ResultProvenance> provenance
) {

    public Offer {
        if (identity == null) {
            throw new IllegalArgumentException("Offer identity must not be null");
        }
        merchantName = trimToNull(merchantName);
        variantTitle = trimToNull(variantTitle);
        availability = availability == null ? OfferAvailability.unknown() : availability;
        delivery = delivery == null ? List.of() : List.copyOf(delivery);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
        if (provenance.isEmpty()) {
            throw new IllegalArgumentException("Offer must retain at least one provenance observation");
        }
    }

    public String key() {
        return identity.key();
    }

    public SellingPlanIdentity sellingPlanIdentity() {
        return identity.sellingPlanIdentity();
    }

    public List<ProductAttribute> selectedOptions() {
        return identity.selectedOptions();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

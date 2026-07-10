package com.meant.api.plugin.catalog.common.dto;

import java.time.Instant;

public record OfferAvailability(
        OfferAvailabilityStatus status,
        Integer quantity,
        Instant availableAt
) {

    public OfferAvailability {
        if (status == null) {
            throw new IllegalArgumentException("Availability status must not be null");
        }
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException("Availability quantity must not be negative");
        }
    }

    public static OfferAvailability unknown() {
        return new OfferAvailability(OfferAvailabilityStatus.UNKNOWN, null, null);
    }
}

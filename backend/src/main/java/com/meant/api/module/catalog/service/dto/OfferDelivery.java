package com.meant.api.module.catalog.service.dto;

public record OfferDelivery(
        DeliveryMethod method,
        String destinationRegion,
        Integer minimumBusinessDays,
        Integer maximumBusinessDays,
        Money cost
) {

    public OfferDelivery {
        if (method == null) {
            throw new IllegalArgumentException("Delivery method must not be null");
        }
        destinationRegion = destinationRegion == null || destinationRegion.isBlank()
                ? null
                : destinationRegion.trim();
        if (minimumBusinessDays != null && minimumBusinessDays < 0
                || maximumBusinessDays != null && maximumBusinessDays < 0
                || minimumBusinessDays != null && maximumBusinessDays != null
                && minimumBusinessDays > maximumBusinessDays) {
            throw new IllegalArgumentException("Delivery day range is invalid");
        }
    }
}

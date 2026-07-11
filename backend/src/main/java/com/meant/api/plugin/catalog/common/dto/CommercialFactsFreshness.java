package com.meant.api.plugin.catalog.common.dto;

public record CommercialFactsFreshness(
        ResultFreshness price,
        ResultFreshness availability,
        ResultFreshness selectedVariant,
        ResultFreshness selectedOptions,
        ResultFreshness fulfillment
) {
    public static CommercialFactsFreshness fromSingleObservation(ResultFreshness freshness) {
        return new CommercialFactsFreshness(freshness, freshness, freshness, freshness, freshness);
    }
}

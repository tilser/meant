package com.meant.api.module.catalog.service.dto;

import java.util.Currency;
import java.util.Locale;

public record CatalogDiscoveryPrice(
        Long min,
        Long max,
        String currency
) {

    public CatalogDiscoveryPrice {
        if ((min != null && min < 0) || (max != null && max < 0)) {
            throw new IllegalArgumentException("Catalog discovery price must not be negative");
        }
        if (min == null && max == null) {
            throw new IllegalArgumentException("Catalog discovery price requires at least one bound");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("Catalog discovery minimum price must not exceed maximum price");
        }
        if (currency != null && !currency.isBlank()) {
            currency = Currency.getInstance(currency.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } else {
            currency = null;
        }
    }

    public CatalogDiscoveryPrice(Long min, Long max) {
        this(min, max, null);
    }
}

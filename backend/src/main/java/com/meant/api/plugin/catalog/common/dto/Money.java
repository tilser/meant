package com.meant.api.plugin.catalog.common.dto;

import java.util.Locale;

/** Monetary value represented only in integer minor units. */
public record Money(long minorUnits, String currency) {

    public Money {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Money currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}

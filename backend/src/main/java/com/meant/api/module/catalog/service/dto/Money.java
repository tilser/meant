package com.meant.api.module.catalog.service.dto;

import java.util.Locale;

/** Monetary value represented only in integer minor units. */
public record Money(long minorUnits, String currency) {

    public Money {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("Money amount must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Money currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}

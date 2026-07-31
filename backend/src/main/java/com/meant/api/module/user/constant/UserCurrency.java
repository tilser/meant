package com.meant.api.module.user.constant;

import com.meant.api.module.user.exception.UserException;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

public final class UserCurrency {

    public static final String DEFAULT = "USD";
    private static final Set<String> SUPPORTED_CODES = Set.of(
            "USD", "EUR", "GBP", "CZK", "CAD", "AUD", "NZD", "JPY", "CHF", "PLN",
            "SEK", "NOK", "DKK", "HUF", "CNY", "HKD", "SGD", "INR", "KRW"
    );

    private UserCurrency() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new UserException("Currency must be one of the supported ISO 4217 currency codes");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            String currency = Currency.getInstance(normalized).getCurrencyCode();
            if (SUPPORTED_CODES.contains(currency)) {
                return currency;
            }
        } catch (IllegalArgumentException exception) {
            // Converted to the same safe validation error below.
        }
        throw new UserException("Currency must be one of the supported ISO 4217 currency codes");
    }

    public static String normalizeOrDefault(String value) {
        return value == null || value.isBlank() ? DEFAULT : normalize(value);
    }

    public static boolean isSupported(String value) {
        return value != null && SUPPORTED_CODES.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    public static Set<String> supportedCodes() {
        return SUPPORTED_CODES;
    }
}

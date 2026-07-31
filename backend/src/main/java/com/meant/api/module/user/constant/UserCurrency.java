package com.meant.api.module.user.constant;

import com.meant.api.module.user.exception.UserException;
import java.util.Currency;
import java.util.Locale;

public final class UserCurrency {

    public static final String DEFAULT = "USD";

    private UserCurrency() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new UserException("Currency must be an ISO 4217 currency code");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return Currency.getInstance(normalized).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new UserException("Currency must be an ISO 4217 currency code");
        }
    }

    public static String normalizeOrDefault(String value) {
        return value == null || value.isBlank() ? DEFAULT : normalize(value);
    }
}

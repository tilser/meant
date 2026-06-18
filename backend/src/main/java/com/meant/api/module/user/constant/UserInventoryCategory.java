package com.meant.api.module.user.constant;

import java.util.Locale;

public enum UserInventoryCategory {
    APPAREL,
    PANTRY,
    HOME,
    OTHER;

    public static UserInventoryCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return UserInventoryCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}

package com.meant.api.module.checkout.service;

import java.util.Map;
import org.springframework.util.StringUtils;

final class NativeCheckoutValueSupport {

    private NativeCheckoutValueSupport() {
    }

    static Object firstMapValue(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    static String scalarString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string.isBlank() ? null : string.trim();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return value.toString();
        }
        return null;
    }

    static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

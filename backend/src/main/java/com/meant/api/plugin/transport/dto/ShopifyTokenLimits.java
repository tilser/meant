package com.meant.api.plugin.transport.dto;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ShopifyTokenLimits(Map<String, Long> values) {

    public ShopifyTokenLimits {
        if (values == null) {
            values = Map.of();
        } else {
            Map<String, Long> clean = new TreeMap<>();
            values.forEach((key, value) -> {
                if (key != null && value != null) {
                    clean.put(key, value);
                }
            });
            values = Collections.unmodifiableMap(clean);
        }
    }

    public static ShopifyTokenLimits none() {
        return new ShopifyTokenLimits(Map.of());
    }
}

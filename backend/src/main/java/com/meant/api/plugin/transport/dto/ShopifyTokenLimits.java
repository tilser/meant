package com.meant.api.plugin.transport.dto;

import java.util.Map;
import java.util.TreeMap;

public record ShopifyTokenLimits(Map<String, Long> values) {

    public ShopifyTokenLimits {
        values = values == null ? Map.of() : Map.copyOf(new TreeMap<>(values));
    }

    public static ShopifyTokenLimits none() {
        return new ShopifyTokenLimits(Map.of());
    }
}

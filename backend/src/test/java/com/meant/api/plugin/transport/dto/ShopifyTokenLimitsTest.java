package com.meant.api.plugin.transport.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShopifyTokenLimitsTest {

    @Test
    void filtersNullKeysAndValuesAtTheMetadataBoundary() {
        Map<String, Long> limits = new HashMap<>();
        limits.put("catalog_per_minute", 120L);
        limits.put("unknown_limit", null);
        limits.put(null, 10L);

        ShopifyTokenLimits result = new ShopifyTokenLimits(limits);

        assertThat(result.values()).containsOnlyKeys("catalog_per_minute");
    }
}

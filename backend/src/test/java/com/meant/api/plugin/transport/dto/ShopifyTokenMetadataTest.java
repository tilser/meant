package com.meant.api.plugin.transport.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyTokenMetadataTest {

    @Test
    void filtersNullScopesAtTheMetadataBoundary() {
        Set<String> scopes = new HashSet<>();
        scopes.add("catalog:read");
        scopes.add(null);

        ShopifyTokenMetadata metadata = new ShopifyTokenMetadata(
                Instant.parse("2026-07-10T12:00:00Z"),
                scopes,
                ShopifyTokenLimits.none()
        );

        assertThat(metadata.scopes()).containsExactly("catalog:read");
    }
}

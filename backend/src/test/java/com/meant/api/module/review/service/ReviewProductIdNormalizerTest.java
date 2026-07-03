package com.meant.api.module.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewProductIdNormalizerTest {

    private final ReviewProductIdNormalizer normalizer = new ReviewProductIdNormalizer();

    @Test
    void keepsNumericShopifyProductIdUnchanged() {
        assertThat(normalizer.normalize("8802707341562")).isEqualTo("8802707341562");
    }

    @Test
    void convertsShopifyGidToNumericProductId() {
        assertThat(normalizer.normalize("gid://shopify/Product/8802707341562")).isEqualTo("8802707341562");
    }

    @Test
    void trimsTrailingSlashFromNumericProductId() {
        assertThat(normalizer.normalize("8802707341562/")).isEqualTo("8802707341562");
    }

    @Test
    void trimsTrailingSlashFromShopifyGid() {
        assertThat(normalizer.normalize("gid://shopify/Product/8802707341562/")).isEqualTo("8802707341562");
    }
}

package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuyerSafeRoutingScopeKeyTest {

    private static final String TECHNICAL_SCOPE =
            "SHOPIFY:merchant:gid://shopify/Shop/1:domain:manningshoes.myshopify.com";

    @Test
    void projectsRoutingScopesToDeterministicOpaqueTokens() {
        String first = BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE);
        String second = BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE);

        assertThat(first)
                .isEqualTo(second)
                .matches("cart_scope_[0-9a-f]{64}")
                .doesNotContain(":domain:", "myshopify.com");
        assertThat(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE.toLowerCase()))
                .isEqualTo(first);
        assertThat(BuyerSafeRoutingScopeKey.project(first)).isEqualTo(first);
    }

    @Test
    void keepsDistinctRoutingScopesDistinctAndMissingScopesAbsent() {
        assertThat(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE + ":other"))
                .isNotEqualTo(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE));
        assertThat(BuyerSafeRoutingScopeKey.project(null)).isNull();
        assertThat(BuyerSafeRoutingScopeKey.project("  ")).isNull();
    }
}

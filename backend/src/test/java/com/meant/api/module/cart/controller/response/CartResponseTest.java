package com.meant.api.module.cart.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import com.meant.api.module.cart.service.dto.CartResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartResponseTest {

    private static final String TECHNICAL_SCOPE =
            "SHOPIFY:merchant:gid://shopify/Shop/1:domain:manningshoes.myshopify.com";

    @Test
    void exposesOnlyTheBuyerSafeRoutingScopeProjection() {
        Instant now = Instant.parse("2026-07-23T20:30:58Z");
        CartResult result = new CartResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "manningshoes.com",
                "SHOPIFY",
                null,
                "gid://shopify/Shop/1",
                TECHNICAL_SCOPE,
                "https://mcp.shopify.com/catalog",
                "remote-cart-1",
                "https://manningshoes.com/checkout",
                "https://manningshoes.com/cart",
                null,
                1,
                "92.00",
                "92.00",
                "USD",
                true,
                now,
                now,
                now.plusSeconds(3_600),
                now,
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        CartResponse response = CartResponse.from(result);

        assertThat(response.routingScopeKey())
                .isEqualTo(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE))
                .doesNotContain(":domain:", "myshopify.com");
        assertThat(response.toString())
                .doesNotContain(TECHNICAL_SCOPE)
                .doesNotContain("mcp.shopify.com");
    }
}

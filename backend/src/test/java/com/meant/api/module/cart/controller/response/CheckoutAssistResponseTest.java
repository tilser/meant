package com.meant.api.module.cart.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.service.dto.CheckoutAssistResult;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutAssistResponseTest {

    @Test
    void neutralizesTransportCoordinatesAtPublicResponseBoundary() {
        CheckoutResult checkout = new CheckoutResult(
                UUID.randomUUID(),
                "gid://shopify/Cart/1",
                "https://merchant.example/checkout",
                null
        );
        CheckoutAssistResult result = new CheckoutAssistResult(
                "Use seller.myshopify.com, then https://transport.example/.well-known/ucp.",
                false,
                checkout
        );

        CheckoutAssistResponse response = CheckoutAssistResponse.from(result);

        assertThat(response.reply())
                .isEqualTo("Use the merchant, then the merchant.")
                .doesNotContain("myshopify.com", "/.well-known/ucp");
    }
}

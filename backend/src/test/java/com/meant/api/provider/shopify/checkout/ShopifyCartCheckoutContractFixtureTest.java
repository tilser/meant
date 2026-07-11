package com.meant.api.provider.shopify.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.plugin.cart.update.dto.UpdateCartArguments;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ShopifyCartCheckoutContractFixtureTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullStateAndUnknownExtensionFixturesRemainTypedAndRedacted() throws Exception {
        UpdateCartArguments update = read("full-cart-update.json", UpdateCartArguments.class);
        UcpCheckoutResponse checkout = read("checkout-unknown-extension.json", UcpCheckoutResponse.class);

        assertThat(update.cart().lineItems()).hasSize(2);
        assertThat(update.cart().lineItems()).extracting(line -> line.item().id())
                .containsExactly("gid://shopify/ProductVariant/redacted-1",
                        "gid://shopify/ProductVariant/redacted-2");
        assertThat(checkout.version()).isEqualTo("2026-04-08");
        assertThat(checkout.resolvedCheckout().status()).isEqualTo("requires_escalation");
        assertThat(checkout.resolvedCheckout().messages()).singleElement()
                .satisfies(message -> assertThat(message.requiresBuyerAction()).isTrue());
    }

    @Test
    void malformedFixtureFailsAsMalformedInsteadOfCreatingPartialState() {
        assertThatThrownBy(() -> read("malformed-checkout.json", UcpCheckoutResponse.class))
                .isInstanceOf(JacksonException.class);
    }

    private <T> T read(String name, Class<T> type) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/shopify-cart-checkout/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return objectMapper.readValue(input, type);
        }
    }
}

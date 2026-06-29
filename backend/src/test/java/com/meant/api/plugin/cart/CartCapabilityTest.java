package com.meant.api.plugin.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.cancel.dto.CancelCartArguments;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.create.CreateCartCapability;
import com.meant.api.plugin.cart.create.dto.CreateCartArguments;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.GetCartCapability;
import com.meant.api.plugin.cart.get.dto.GetCartArguments;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.UpdateCartCapability;
import com.meant.api.plugin.cart.update.dto.UpdateCartArguments;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createBuildsTypedArgumentsAndParsesTypedResponse() {
        CreateCartCapability capability = new CreateCartCapability(objectMapper);

        CreateCartArguments arguments = capability.buildArguments(
                new CreateCartRequest(
                        List.of(new CartAddItem("gid://shopify/ProductVariant/1", 1)),
                        Map.of("email", "ada@example.com"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("SAVE5"),
                        List.of("CARD1234"),
                        "Please gift wrap"
                ),
                NegotiatedCapabilities.none()
        );
        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(cartResponseJson(), null,
                NegotiatedCapabilities.none()));

        assertThat(arguments.addItems()).extracting("productVariantId")
                .containsExactly("gid://shopify/ProductVariant/1");
        assertThat(arguments.discountCodes()).containsExactly("SAVE5");
        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.cart().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(response.cart().expiresAt()).isEqualTo(Instant.parse("2026-06-16T12:05:00Z"));
        assertThat(response.messages()).extracting("code").containsExactly("cart_created");
    }

    @Test
    void getBuildsTypedArgumentsAndParsesStructuredContentResponse() {
        GetCartCapability capability = new GetCartCapability(objectMapper);

        GetCartArguments arguments = capability.buildArguments(
                new GetCartRequest("gid://shopify/Cart/1"),
                NegotiatedCapabilities.none()
        );
        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(null,
                Map.of("cart", Map.of("id", "gid://shopify/Cart/1"), "errors", List.of()),
                NegotiatedCapabilities.none()));

        assertThat(arguments.cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/1");
    }

    @Test
    void updateBuildsTypedArgumentsAndParsesTypedResponse() {
        UpdateCartCapability capability = new UpdateCartCapability(objectMapper);

        UpdateCartArguments arguments = capability.buildArguments(
                new UpdateCartRequest(
                        "gid://shopify/Cart/1",
                        List.of(new CartAddItem("gid://shopify/ProductVariant/2", 1)),
                        List.of(new CartUpdateItem("gid://shopify/CartLine/1", 2)),
                        List.of("gid://shopify/CartLine/2"),
                        null,
                        List.of(),
                        List.of(),
                        List.of(Map.of("delivery_option_handle", "express")),
                        null,
                        null,
                        null
                ),
                NegotiatedCapabilities.none()
        );
        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(cartResponseJson(), null,
                NegotiatedCapabilities.none()));

        assertThat(arguments.cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(arguments.updateItems()).extracting("id").containsExactly("gid://shopify/CartLine/1");
        assertThat(arguments.removeLineIds()).containsExactly("gid://shopify/CartLine/2");
        assertThat(response.cart().lines()).hasSize(1);
    }

    @Test
    void cancelBuildsTypedArgumentsAndParsesTypedResponse() {
        CancelCartCapability capability = new CancelCartCapability(objectMapper);

        CancelCartArguments arguments = capability.buildArguments(
                new CancelCartRequest("gid://shopify/Cart/1"),
                NegotiatedCapabilities.none()
        );
        CancelCartResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {"cart_id":"gid://shopify/Cart/1","status":"canceled","canceled":true}
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(arguments.cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.cartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.canceled()).isTrue();
    }

    private String cartResponseJson() {
        return """
                {
                  "instructions": "Checkout when ready",
                  "messages": [
                    {
                      "code": "cart_created",
                      "severity": "info",
                      "message": "Cart created"
                    }
                  ],
                  "cart": {
                    "id": "gid://shopify/Cart/1",
                    "created_at": "2026-06-16T11:05:00Z",
                    "updated_at": "2026-06-16T11:05:01Z",
                    "expires_at": "2026-06-16T12:05:00Z",
                    "continue_url": "https://merchant.example/continue",
                    "lines": [
                      {
                        "id": "gid://shopify/CartLine/1",
                        "quantity": 1,
                        "cost": {
                          "total_amount": {"amount": "14.95", "currency": "USD"},
                          "subtotal_amount": {"amount": "14.95", "currency": "USD"}
                        },
                        "merchandise": {
                          "id": "gid://shopify/ProductVariant/1",
                          "title": "3x6",
                          "product": {
                            "id": "gid://shopify/Product/1",
                            "title": "Candle"
                          }
                        }
                      }
                    ],
                    "cost": {
                      "total_amount": {"amount": "14.95", "currency": "USD"},
                      "subtotal_amount": {"amount": "14.95", "currency": "USD"}
                    },
                    "total_quantity": 1
                  },
                  "errors": []
                }
                """;
    }
}

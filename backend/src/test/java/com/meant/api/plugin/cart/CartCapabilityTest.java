package com.meant.api.plugin.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.cancel.dto.CancelCartArguments;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
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
import com.meant.api.plugin.cart.update.dto.CartReplacementState;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.plugin.support.UcpAttribution;
import com.meant.api.plugin.transport.profile.AgentAttributionProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartCapabilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAttributionProperties attributionProperties = new AgentAttributionProperties(
            "app.usemeant.com", "meant", "agentic_commerce");
    private final UcpAttribution attribution = attributionProperties.attribution();

    @Test
    void createBuildsTypedArgumentsAndParsesTypedResponse() throws Exception {
        CreateCartCapability capability = new CreateCartCapability(objectMapper, attributionProperties);

        CreateCartArguments arguments = capability.buildArguments(
                new CreateCartRequest(
                        List.of(new CartAddItem("gid://shopify/ProductVariant/1", 1)),
                        new CartBuyer(null, null, "ada@example.com", null),
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

        assertThat(arguments.cart().lineItems()).extracting(lineItem -> lineItem.item().id())
                .containsExactly("gid://shopify/ProductVariant/1");
        assertThat(arguments.cart().lineItems().getFirst().item().productId()).isNull();
        assertThat(objectMapper.writeValueAsString(arguments)).doesNotContain("product_id");
        assertThat(arguments.cart().discounts().codes()).containsExactly("SAVE5");
        assertThat(arguments.cart().giftCardCodes()).containsExactly("CARD1234");
        assertThat(arguments.cart().note()).isEqualTo("Please gift wrap");
        assertThat(arguments.cart().attribution()).isEqualTo(attribution);
        assertThat(objectMapper.writeValueAsString(arguments)).contains(
                "\"attribution\":{\"referring_domain\":\"app.usemeant.com\","
                        + "\"utm_source\":\"meant\",\"utm_medium\":\"agentic_commerce\"}");
        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.cart().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(response.cart().expiresAt()).isEqualTo(Instant.parse("2026-06-16T12:05:00Z"));
        assertThat(response.messages()).extracting("code").containsExactly("cart_created");
    }

    @Test
    void createCarriesExactConfiguredOfferIdentityIntoTheRemoteCall() {
        CreateCartCapability capability = new CreateCartCapability(objectMapper, attributionProperties);
        CartAddItem item = new CartAddItem(
                "product-1",
                "variant-1",
                List.of(new CartAddItem.SelectedOption("variant", "Color", "Black")),
                List.of(new CartAddItem.Component("component-product", "component-variant", 2,
                        List.of(new CartAddItem.SelectedOption(null, "Size", "M")))),
                new CartAddItem.SellingPlan(null, "plan-1", List.of(new CartAddItem.Option("Delivery", "Monthly"))),
                1);

        CreateCartArguments arguments = capability.buildArguments(
                new CreateCartRequest(List.of(item), null, List.of(), List.of(), List.of(), List.of(), List.of(), null),
                NegotiatedCapabilities.none());

        CartToolArguments.Item remote = arguments.cart().lineItems().getFirst().item();
        assertThat(remote.id()).isEqualTo("variant-1");
        assertThat(remote.productId()).isEqualTo("product-1");
        assertThat(remote.selectedOptions()).containsExactlyElementsOf(item.selectedOptions());
        assertThat(remote.components()).containsExactlyElementsOf(item.components());
        assertThat(remote.sellingPlan()).isEqualTo(item.sellingPlan());
    }

    @Test
    void getBuildsTypedArgumentsAndParsesStructuredContentResponse() {
        GetCartCapability capability = new GetCartCapability(objectMapper);

        GetCartArguments arguments = capability.buildArguments(
                new GetCartRequest("gid://shopify/Cart/1"),
                NegotiatedCapabilities.none()
        );
        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(null,
                objectMapper.valueToTree(
                        Map.of("cart", Map.of("id", "gid://shopify/Cart/1"), "errors", List.of())),
                NegotiatedCapabilities.none()));

        assertThat(arguments.id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/1");
    }

    @Test
    void updateBuildsTypedArgumentsAndParsesTypedResponse() {
        UpdateCartCapability capability = new UpdateCartCapability(objectMapper, attributionProperties);

        UpdateCartArguments arguments = capability.buildArguments(
                new UpdateCartRequest(
                        "gid://shopify/Cart/1",
                        List.of(new CartAddItem("gid://shopify/ProductVariant/2", 1)),
                        List.of(new CartUpdateItem(
                                "gid://shopify/CartLine/1",
                                "gid://shopify/ProductVariant/2",
                                2
                        )),
                        List.of("gid://shopify/CartLine/2"),
                        List.of(new CartUpdateItem(
                                "gid://shopify/CartLine/2",
                                "gid://shopify/ProductVariant/3",
                                0
                        )),
                        null,
                        List.of(),
                        List.of(),
                        List.of(new CartDeliveryOptionSelection(null, "delivery-group", "express")),
                        null,
                        null,
                        null
                ),
                NegotiatedCapabilities.none()
        );
        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(cartResponseJson(), null,
                NegotiatedCapabilities.none()));

        assertThat(arguments.id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(arguments.cart().lineItems()).extracting("id")
                .containsExactly(null, "gid://shopify/CartLine/1", "gid://shopify/CartLine/2");
        assertThat(arguments.cart().lineItems().get(1).item().id())
                .isEqualTo("gid://shopify/ProductVariant/2");
        assertThat(arguments.cart().lineItems().get(2).item().id())
                .isEqualTo("gid://shopify/ProductVariant/3");
        assertThat(arguments.cart().lineItems().get(2).quantity()).isZero();
        assertThat(arguments.cart().attribution()).isEqualTo(attribution);
        assertThat(response.cart().lines()).hasSize(1);
    }

    @Test
    void updateDistinguishesUnchangedDiscountsFromExplicitClear() throws Exception {
        UpdateCartCapability capability = new UpdateCartCapability(objectMapper, attributionProperties);

        UpdateCartArguments unchanged = capability.buildArguments(updateRequest(null), NegotiatedCapabilities.none());
        UpdateCartArguments cleared = capability.buildArguments(updateRequest(List.of()), NegotiatedCapabilities.none());

        assertThat(objectMapper.writeValueAsString(unchanged)).doesNotContain("\"discounts\"");
        assertThat(objectMapper.writeValueAsString(cleared))
                .contains("\"discounts\":{\"codes\":[]}");
    }

    @Test
    void providerUpdateSerializesTheCompleteIntendedCartState() {
        UpdateCartCapability capability = new UpdateCartCapability(objectMapper, attributionProperties);
        CartAddItem first = new CartAddItem("product-1", "variant-1",
                List.of(new CartAddItem.SelectedOption("variant", "Color", "Black")),
                List.of(), null, 3);
        CartAddItem second = new CartAddItem("product-2", "variant-2",
                List.of(), List.of(), null, 1);

        UpdateCartArguments arguments = capability.buildArguments(new UpdateCartRequest(
                "cart-1", List.of(), List.of(), List.of(), List.of(), null, new CartContext("US"),
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                new CartReplacementState(List.of(first, second), null, new CartContext("US"),
                        null, null, null, List.of(), null)),
                NegotiatedCapabilities.none());

        assertThat(arguments.cart().lineItems()).hasSize(2);
        assertThat(arguments.cart().lineItems()).extracting(item -> item.item().id())
                .containsExactly("variant-1", "variant-2");
        assertThat(arguments.cart().lineItems()).extracting(CartToolArguments.LineItem::quantity)
                .containsExactly(3, 1);
        assertThat(arguments.cart().lineItems().getFirst().item().selectedOptions())
                .containsExactlyElementsOf(first.selectedOptions());
        assertThat(arguments.cart().context().addressCountry()).isEqualTo("US");
        assertThat(arguments.cart().attribution()).isEqualTo(attribution);
    }

    private UpdateCartRequest updateRequest(List<String> discountCodes) {
        return new UpdateCartRequest(
                "cart-1", List.of(), List.of(), List.of(), List.of(), null, null,
                null, null, null, discountCodes, null, null);
    }

    @Test
    void providerUpdateSerializesRequiredEmptyLineItemsWhenRemovingTheLastItem() throws Exception {
        UpdateCartCapability capability = new UpdateCartCapability(objectMapper, attributionProperties);
        UpdateCartArguments arguments = capability.buildArguments(new UpdateCartRequest(
                "cart-1", List.of(), List.of(), List.of(), List.of(), null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                new CartReplacementState(List.of(), null, null, null,
                        null, null, List.of(), null)),
                NegotiatedCapabilities.none());

        assertThat(arguments.cart().lineItems()).isEmpty();
        assertThat(objectMapper.writeValueAsString(arguments))
                .contains("\"cart\":{\"line_items\":[]");
    }

    @Test
    void providerUpdatePreservesUnknownRemoteExtensionFields() throws Exception {
        UcpCartResponse remote = new GetCartCapability(objectMapper).parseResponse(new UcpToolResponse(
                """
                        {
                          "cart": {
                            "id": "cart-1",
                            "lines": [],
                            "buyer": {
                              "email": "buyer@example.test",
                              "com.shopify.buyer_token": "buyer-token"
                            },
                            "context": {
                              "address_country": "US",
                              "com.shopify.context_token": "context-token"
                            },
                            "signals": {
                              "dev.ucp.buyer_ip": "192.0.2.10",
                              "com.shopify.signal_token": "signal-token"
                            },
                            "fulfillment": {
                              "com.shopify.fulfillment_token": "fulfillment-token",
                              "methods": [
                                {
                                  "id": "shipping-1",
                                  "type": "shipping",
                                  "com.shopify.method_token": "method-token",
                                  "destinations": [
                                    {
                                      "id": "home",
                                      "postal_code": "10001",
                                      "com.shopify.destination_token": "destination-token"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "id": "delivery-1",
                                      "selected_option_id": "express",
                                      "com.shopify.group_token": "group-token",
                                      "options": [
                                        {
                                          "id": "express",
                                          "title": "Express",
                                          "com.shopify.option_token": "option-token"
                                        }
                                      ]
                                    }
                                  ]
                                }
                              ]
                            }
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()));
        CartReplacementState replacement = new CartReplacementState(
                List.of(),
                remote.cart().buyer(),
                remote.cart().context(),
                remote.cart().signals(),
                remote.cart().fulfillment(),
                remote.cart().discounts(),
                List.of(),
                remote.cart().note());

        UpdateCartArguments arguments = new UpdateCartCapability(objectMapper, attributionProperties).buildArguments(
                new UpdateCartRequest(
                        "cart-1", List.of(), List.of(), List.of(), List.of(), null, null,
                        List.of(), List.of(), List.of(), List.of(), List.of(), null, replacement),
                NegotiatedCapabilities.none());
        String serialized = objectMapper.writeValueAsString(arguments);

        assertThat(remote.cart().buyer().extensions()).containsKey("com.shopify.buyer_token");
        assertThat(remote.cart().context().extensions()).containsKey("com.shopify.context_token");
        assertThat(remote.cart().signals().extensions()).containsKey("com.shopify.signal_token");
        assertThat(remote.cart().fulfillment().extensions()).containsKey("com.shopify.fulfillment_token");
        assertThat(serialized)
                .contains("\"com.shopify.buyer_token\":\"buyer-token\"")
                .contains("\"com.shopify.context_token\":\"context-token\"")
                .contains("\"com.shopify.signal_token\":\"signal-token\"")
                .contains("\"com.shopify.fulfillment_token\":\"fulfillment-token\"")
                .contains("\"com.shopify.method_token\":\"method-token\"")
                .contains("\"com.shopify.destination_token\":\"destination-token\"")
                .contains("\"com.shopify.group_token\":\"group-token\"")
                .contains("\"com.shopify.option_token\":\"option-token\"");
    }

    @Test
    void createParsesShopifyRootCartResponse() {
        CreateCartCapability capability = new CreateCartCapability(objectMapper, attributionProperties);

        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "id": "gid://shopify/Cart/root",
                          "line_items": [
                            {
                              "id": "gid://shopify/CartLine/1",
                              "item": {
                                "id": "gid://shopify/ProductVariant/1",
                                "title": "Pocket T-Shirt - Black / S"
                              },
                              "quantity": 1,
                              "totals": [
                                {"type": "subtotal", "amount": 4900, "display_text": "Subtotal"},
                                {"type": "total", "amount": 4900, "display_text": "Total"}
                              ]
                            }
                          ],
                          "currency": "USD",
                          "totals": [
                            {"type": "subtotal", "amount": 4900, "display_text": "Subtotal"},
                            {"type": "total", "amount": 4900, "display_text": "Total"}
                          ],
                          "expires_at": "2026-07-30T20:48:44Z",
                          "discounts": {"codes": [], "applied": []}
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/root");
        assertThat(response.cart().lines()).hasSize(1);
        assertThat(response.cart().lines().getFirst().merchandise().id())
                .isEqualTo("gid://shopify/ProductVariant/1");
        assertThat(response.cart().cost().totalAmount().amount()).isEqualTo(4900);
        assertThat(response.cart().cost().totalAmount().currency()).isEqualTo("USD");
    }

    @Test
    void createParsesShopifyConnectionCartLines() {
        CreateCartCapability capability = new CreateCartCapability(objectMapper, attributionProperties);

        UcpCartResponse response = capability.parseResponse(new UcpToolResponse(
                """
                        {
                          "cart": {
                            "id": "gid://shopify/Cart/connection",
                            "lines": {
                              "edges": [
                                {
                                  "node": {
                                    "id": "gid://shopify/CartLine/connection-1",
                                    "quantity": 2,
                                    "cost": {
                                      "totalAmount": {"amount": "38.00", "currencyCode": "USD"},
                                      "subtotalAmount": {"amount": "38.00", "currencyCode": "USD"}
                                    },
                                    "merchandise": {
                                      "id": "gid://shopify/ProductVariant/connection-1",
                                      "title": "Pocket T-Shirt - Black / S",
                                      "product": {
                                        "id": "gid://shopify/Product/connection-1",
                                        "title": "Pocket T-Shirt"
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        }
                        """,
                null,
                NegotiatedCapabilities.none()
        ));

        assertThat(response.cart().id()).isEqualTo("gid://shopify/Cart/connection");
        assertThat(response.cart().lines()).hasSize(1);
        assertThat(response.cart().lines().getFirst().id()).isEqualTo("gid://shopify/CartLine/connection-1");
        assertThat(response.cart().lines().getFirst().quantity()).isEqualTo(2);
        assertThat(response.cart().lines().getFirst().merchandise().id())
                .isEqualTo("gid://shopify/ProductVariant/connection-1");
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

        assertThat(arguments.id()).isEqualTo("gid://shopify/Cart/1");
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

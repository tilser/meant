package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartResultMapperTest {

    private static final String MERCHANT_DOMAIN = "allbirds.com";
    private static final String ROUTING_DOMAIN = "weareallbirds.myshopify.com";
    private static final String MCP_ENDPOINT =
            "https://weareallbirds.myshopify.com/api/ucp/mcp";
    private static final String ADVERTISED_ENDPOINT =
            "https://advertised.shopify-transport.example/api/ucp/mcp";
    private static final String PROFILE_ENDPOINT =
            "https://profile.shopify-transport.example/.well-known/ucp";
    private static final String INTEGRATION_ENDPOINT =
            "https://integration.shopify-transport.example/custom/cart";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CartResultMapper mapper = new CartResultMapper(objectMapper);

    @Test
    void sanitizesTransportCoordinatesInCartInstructionsAndErrors() throws Exception {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .merchantDomain(MERCHANT_DOMAIN)
                .routingDomain(ROUTING_DOMAIN)
                .endpoint(MCP_ENDPOINT)
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .rawCartResponse("{}")
                .totalQuantity(0)
                .build();
        UcpCartResponse response = objectMapper.readValue("""
                {
                  "instructions": "Background profile: https://profile.shopify-transport.example/.well-known/ucp",
                  "cart": {
                    "id": "gid://shopify/Cart/1",
                    "lines": [],
                    "total_quantity": 0,
                    "checkout_url": "https://allbirds.com/checkouts/1"
                  },
                  "messages": [
                    {
                      "type": "error",
                      "severity": "error",
                      "code": "cart_rejected",
                      "message": "Cart failed at https://advertised.shopify-transport.example/api/ucp/mcp. Product: https://integration.shopify-transport.example/products/tree-runner",
                      "target": "integration.shopify-transport.example"
                    }
                  ],
                  "errors": []
                }
                """, UcpCartResponse.class);
        MerchantCartProvider provider = provider();

        CartResult result = mapper.from(cart, response, provider);

        assertThat(result.instructions())
                .isEqualTo("Background profile: allbirds.com")
                .doesNotContain(PROFILE_ENDPOINT, "/.well-known/ucp");
        assertThat(result.checkoutUrl()).isEqualTo("https://allbirds.com/checkouts/1");
        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.message())
                    .contains("Cart failed at allbirds.com")
                    .contains("https://allbirds.com/products/tree-runner")
                    .doesNotContain(
                            "advertised.shopify-transport.example",
                            "integration.shopify-transport.example",
                            "/api/ucp/mcp"
                    );
            assertThat(message.target()).isEqualTo("allbirds.com");
        });
    }

    @Test
    void sanitizesDistinctProfileAliasInStoredCartResponse() {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .merchantDomain(MERCHANT_DOMAIN)
                .routingDomain(ROUTING_DOMAIN)
                .endpoint(MCP_ENDPOINT)
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("stored-hash")
                .rawCartResponse("""
                        {
                          "instructions": "Profile https://profile.shopify-transport.example/.well-known/ucp",
                          "cart": {
                            "id": "gid://shopify/Cart/1",
                            "lines": [],
                            "total_quantity": 0
                          },
                          "messages": [
                            {
                              "type": "warning",
                              "severity": "warning",
                              "code": "stored_notice",
                              "message": "Transport: profile.shopify-transport.example"
                            }
                          ],
                          "errors": []
                        }
                        """)
                .totalQuantity(0)
                .build();

        CartResult result = mapper.from(cart, provider());

        assertThat(result.instructions()).isEqualTo("Profile allbirds.com");
        assertThat(result.messages()).singleElement().satisfies(message ->
                assertThat(message.message())
                        .isEqualTo("Transport: allbirds.com")
                        .doesNotContain("profile.shopify-transport.example"));
    }

    @Test
    void sanitizesTransportCoordinatesAcrossAllBuyerVisibleCartText() throws Exception {
        Instant now = Instant.parse("2026-07-23T18:30:00Z");
        CartLine line = CartLine.builder()
                .remoteCartLineId("line-1")
                .productId("product-1")
                .productTitle("Stored product")
                .productBrand("Brand from integration.shopify-transport.example")
                .productVariantId("variant-1")
                .variantTitle("Stored variant")
                .quantity(1)
                .offerKey("offer-1")
                .selectedOptionsJson("""
                        [{"name":"Source","value":"profile.shopify-transport.example"}]
                        """)
                .componentsJson("""
                        [{"title":"Component from advertised.shopify-transport.example"}]
                        """)
                .sellingPlanJson("""
                        {"name":"Plan at https://integration.shopify-transport.example/custom/cart"}
                        """)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
        CartAppliedCode appliedCode = CartAppliedCode.builder()
                .type(CartAppliedCodeType.DISCOUNT)
                .code("SAVE")
                .label("Discount from profile.shopify-transport.example")
                .applicable(true)
                .displayOrder(0)
                .build();
        String rawResponse = """
                {
                  "cart": {
                    "id": "gid://shopify/Cart/1",
                    "lines": [
                      {
                        "id": "line-1",
                        "quantity": 1,
                        "merchandise": {
                          "id": "variant-1",
                          "title": "Variant at advertised.shopify-transport.example",
                          "product": {
                            "id": "product-1",
                            "title": "Product at integration.shopify-transport.example"
                          }
                        }
                      }
                    ],
                    "total_quantity": 1,
                    "checkout_url": "https://advertised.shopify-transport.example/api/ucp/mcp/session/1",
                    "continue_url": "https://unlisted.transport.example/api/mcp",
                    "delivery_groups": [
                      {
                        "id": "delivery-1",
                        "handle": "shipping",
                        "delivery_options": [
                          {
                            "handle": "standard",
                            "title": "Ships through advertised.shopify-transport.example",
                            "description": "Details: https://profile.shopify-transport.example/.well-known/ucp",
                            "code": "integration.shopify-transport.example",
                            "delivery_method_type": "MCP at profile.shopify-transport.example",
                            "delivery_estimate": "See advertised.shopify-transport.example",
                            "estimated_delivery_time": "From integration.shopify-transport.example",
                            "selected": true
                          }
                        ]
                      }
                    ]
                  },
                  "messages": [],
                  "errors": []
                }
                """;
        UcpCartResponse response = objectMapper.readValue(rawResponse, UcpCartResponse.class);
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .merchantDomain(MERCHANT_DOMAIN)
                .routingDomain(ROUTING_DOMAIN)
                .endpoint(MCP_ENDPOINT)
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("complete-text-hash")
                .checkoutUrl("https://weareallbirds.myshopify.com/checkouts/fallback")
                .continueUrl("https://allbirds.com/checkouts/fallback")
                .rawCartResponse(rawResponse)
                .totalQuantity(1)
                .lines(List.of(line))
                .appliedCodes(List.of(appliedCode))
                .build();

        assertThat(mapper.storedBuyerTextMayContainTransport(cart)).isTrue();

        CartResult result = mapper.from(cart, response, provider());

        assertThat(result.checkoutUrl())
                .isEqualTo("https://weareallbirds.myshopify.com/checkouts/fallback");
        assertThat(result.continueUrl()).isEqualTo("https://allbirds.com/checkouts/fallback");
        assertThat(result.appliedCodes()).singleElement().satisfies(code ->
                assertThat(code.label()).isEqualTo("Discount from allbirds.com"));
        assertThat(result.lines()).singleElement().satisfies(cartLine -> {
            assertThat(cartLine.productTitle()).isEqualTo("Product at allbirds.com");
            assertThat(cartLine.productBrand()).isEqualTo("Brand from allbirds.com");
            assertThat(cartLine.variantTitle()).isEqualTo("Variant at allbirds.com");
            assertThat(cartLine.selectedOptionsJson()).contains("allbirds.com")
                    .doesNotContain("profile.shopify-transport.example");
            assertThat(cartLine.componentsJson()).contains("allbirds.com")
                    .doesNotContain("advertised.shopify-transport.example");
            assertThat(cartLine.sellingPlanJson()).contains("allbirds.com")
                    .doesNotContain("integration.shopify-transport.example");
        });
        assertThat(result.deliveryGroups()).singleElement().satisfies(group ->
                assertThat(group.deliveryOptions()).singleElement().satisfies(option -> {
                    assertThat(option.title()).isEqualTo("Ships through allbirds.com");
                    assertThat(option.description()).isEqualTo("Details: allbirds.com");
                    assertThat(option.code()).isEqualTo("allbirds.com");
                    assertThat(option.deliveryMethodType()).isEqualTo("MCP at allbirds.com");
                    assertThat(option.deliveryEstimate()).isEqualTo("See allbirds.com");
                    assertThat(option.estimatedDeliveryTime()).isEqualTo("From allbirds.com");
                }));
    }

    private MerchantCartProvider provider() {
        return new MerchantCartProvider(
                UUID.randomUUID(),
                MERCHANT_DOMAIN,
                ROUTING_DOMAIN,
                ADVERTISED_ENDPOINT,
                PROFILE_ENDPOINT,
                List.of(new MerchantIntegrationRouting(
                        UUID.randomUUID(),
                        MerchantIntegrationProvider.SHOPIFY,
                        Set.of(MerchantIntegrationRole.CART),
                        MerchantIntegrationStatus.ACTIVE,
                        "gid://shopify/Shop/1",
                        "integration.shopify-transport.example",
                        "gid://shopify/Shop/1",
                        INTEGRATION_ENDPOINT
                )),
                MerchantExecutionPolicy.unavailable(),
                null,
                Set.of("dev.ucp.shopping.cart")
        );
    }
}

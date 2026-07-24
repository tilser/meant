package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutResultMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CheckoutResultMapper mapper =
            new CheckoutResultMapper(objectMapper, new CheckoutExecutionPlanner());

    @Test
    void sanitizesAllKnownProviderAliasesWithoutChangingCheckoutUrl() throws Exception {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .merchantDomain("allbirds.com")
                .routingDomain("weareallbirds.myshopify.com")
                .endpoint("https://weareallbirds.myshopify.com/api/ucp/mcp")
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .rawCartResponse("{}")
                .totalQuantity(1)
                .build();
        UcpCheckoutResponse response = objectMapper.readValue("""
                {
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "incomplete",
                    "checkout_url": "https://allbirds.com/checkouts/1",
                    "messages": [
                      {
                        "type": "warning",
                        "code": "merchant_notice",
                        "severity": "recoverable",
                        "content": "Advertised https://advertised.transport.example/api/ucp/mcp; profile.transport.example; product https://integration.transport.example/products/tree-runner",
                        "target": "https://profile.transport.example/.well-known/ucp"
                      }
                    ]
                  },
                  "messages": [],
                  "errors": []
                }
                """, UcpCheckoutResponse.class);
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(),
                "allbirds.com",
                "weareallbirds.myshopify.com",
                "https://advertised.transport.example/api/ucp/mcp",
                "https://profile.transport.example/.well-known/ucp",
                List.of(new MerchantIntegrationRouting(
                        UUID.randomUUID(),
                        MerchantIntegrationProvider.SHOPIFY,
                        Set.of(MerchantIntegrationRole.CHECKOUT),
                        MerchantIntegrationStatus.ACTIVE,
                        "gid://shopify/Shop/1",
                        "integration.transport.example",
                        "gid://shopify/Shop/1",
                        "https://integration.transport.example/custom/checkout"
                )),
                MerchantExecutionPolicy.unavailable(),
                null,
                Set.of("dev.ucp.shopping.checkout")
        );

        CheckoutResult result = mapper.from(cart, response, provider);

        assertThat(result.checkoutUrl()).isEqualTo("https://allbirds.com/checkouts/1");
        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.content())
                    .contains("Advertised allbirds.com")
                    .contains("allbirds.com; product")
                    .contains("https://allbirds.com/products/tree-runner")
                    .doesNotContain(
                            "advertised.transport.example",
                            "profile.transport.example",
                            "integration.transport.example",
                            "/api/ucp/mcp"
                    );
            assertThat(message.path()).isEqualTo("allbirds.com");
        });
    }

    @Test
    void rejectsProtocolAndKnownEndpointHandoffsButKeepsLegitimateSameHostCheckout() throws Exception {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID())
                .merchantDomain("allbirds.com")
                .routingDomain("weareallbirds.myshopify.com")
                .endpoint("https://weareallbirds.myshopify.com/api/ucp/mcp")
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("handoff-hash")
                .checkoutUrl("https://weareallbirds.myshopify.com/checkouts/fallback")
                .continueUrl("https://allbirds.com/checkouts/fallback")
                .rawCartResponse("{}")
                .totalQuantity(1)
                .build();
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(),
                "allbirds.com",
                "weareallbirds.myshopify.com",
                "https://advertised.transport.example/api/ucp/mcp",
                "https://profile.transport.example/.well-known/ucp",
                List.of(),
                MerchantExecutionPolicy.unavailable(),
                null,
                Set.of("dev.ucp.shopping.checkout")
        );
        UcpCheckoutResponse response = objectMapper.readValue("""
                {
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "incomplete",
                    "checkout_url": "https://advertised.transport.example/api/ucp/mcp/session/1",
                    "continue_url": "https://unlisted.transport.example/api/mcp"
                  },
                  "messages": [],
                  "errors": []
                }
                """, UcpCheckoutResponse.class);

        CheckoutResult result = mapper.from(cart, response, provider);

        assertThat(result.checkoutUrl())
                .isEqualTo("https://weareallbirds.myshopify.com/checkouts/fallback");
        assertThat(result.continueUrl()).isEqualTo("https://allbirds.com/checkouts/fallback");
    }
}

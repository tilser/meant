package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.exception.MerchantMcpToolException;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddress;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.create.CreateCartCapability;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.GetCartCapability;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.UpdateCartCapability;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MerchantCartPluginDispatchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesCartLifecycleAndMapsGetAfterCancelToNotFound() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCartPluginDispatchService service = new MerchantCartPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper,
                List.of(),
                new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
        );
        MerchantCartProvider provider = provider();
        UcpSession session = UcpSession.start();

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"create_cart\"")))
                .andExpect(content().string(containsString("\"cart\"")))
                .andExpect(content().string(containsString("\"line_items\"")))
                .andRespond(withSuccess(mcpResponse(cartResponse("gid://shopify/Cart/1")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"update_cart\"")))
                .andExpect(content().string(containsString("\"id\":\"gid://shopify/Cart/1\"")))
                .andRespond(withSuccess(mcpResponse(cartResponse("gid://shopify/Cart/1")),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"cancel_cart\"")))
                .andExpect(content().string(containsString("\"id\":\"gid://shopify/Cart/1\"")))
                .andRespond(withSuccess(mcpResponse("""
                        {"cart_id":"gid://shopify/Cart/1","status":"canceled","canceled":true}
                        """), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"get_cart\"")))
                .andRespond(withSuccess(mcpResponse("""
                        {
                          "messages": [
                            {
                              "code": "not_found",
                              "severity": "error",
                              "message": "Cart not found"
                            }
                          ],
                          "errors": []
                        }
                        """), MediaType.APPLICATION_JSON));

        UcpCartToolResult created = service.createCart(
                provider,
                new CreateCartRequest(
                        List.of(new CartAddItem("gid://shopify/ProductVariant/1", 1)),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                ),
                session
        );
        UcpCartToolResult updated = service.updateCart(
                provider,
                new UpdateCartRequest(
                        session.cartId(),
                        List.of(new CartAddItem("gid://shopify/ProductVariant/2", 1)),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                ),
                session
        );
        service.cancelCart(provider, new CancelCartRequest("gid://shopify/Cart/1"), session);

        assertThat(created.response().cart().id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(updated.response().cart().id()).isEqualTo("gid://shopify/Cart/1");
        assertThat(session.cartId()).isNull();
        assertThatThrownBy(() -> service.getCart(provider, new GetCartRequest("gid://shopify/Cart/1"), session))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: gid://shopify/Cart/1")
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        server.verify();
    }

    @Test
    void boundGenericCreateFailureUsesOneExactEndpointCallAndNoLegacyFallback() {
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callToolExactEndpoint(any(), any(), any(), any()))
                .thenThrow(new MerchantMcpToolException("ambiguous timeout"));
        MerchantCartPluginDispatchService service = new MerchantCartPluginDispatchService(
                client, registry(), objectMapper, List.of(),
                new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        MerchantCartProvider provider = provider();
        CartRoutingTarget target = new CartRoutingTarget(
                "GENERIC_UCP:integration:" + UUID.randomUUID(), MerchantIntegrationProvider.GENERIC_UCP,
                UUID.randomUUID(), null, provider);

        assertThatThrownBy(() -> service.createCart(target, new CreateCartRequest(
                List.of(new CartAddItem("variant-1", 1)), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), null), UcpSession.start()))
                .isInstanceOf(CartException.class);

        verify(client).callToolExactEndpoint(eq(provider), eq("create_cart"), any(), eq(java.util.Map.of()));
        verify(client, never()).callTool(any(MerchantCartProvider.class), any(), any(), any());
    }

    @Test
    void boundGenericUpdateFailureUsesOneExactEndpointCallAndNoLegacyFallback() {
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callToolExactEndpoint(any(), any(), any(), any()))
                .thenThrow(new MerchantMcpToolException("ambiguous timeout"));
        MerchantCartPluginDispatchService service = new MerchantCartPluginDispatchService(
                client, registry(), objectMapper, List.of(),
                new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        MerchantCartProvider provider = provider();
        CartRoutingTarget target = new CartRoutingTarget(
                "GENERIC_UCP:integration:" + UUID.randomUUID(), MerchantIntegrationProvider.GENERIC_UCP,
                UUID.randomUUID(), null, provider);

        assertThatThrownBy(() -> service.updateCart(target, new UpdateCartRequest(
                "cart-1", List.of(new CartAddItem("variant-1", 1)), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null), UcpSession.start()))
                .isInstanceOf(CartException.class);

        verify(client).callToolExactEndpoint(eq(provider), eq("update_cart"), any(), eq(java.util.Map.of()));
        verify(client, never()).callTool(any(MerchantCartProvider.class), any(), any(), any());
    }

    @Test
    void legacyCreateRetainsCompatibilityFallback() {
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callTool(any(MerchantCartProvider.class), eq("create_cart"), any(), any()))
                .thenThrow(new MerchantMcpToolException("primary contract unsupported"))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://merchant.example/api/mcp", cartResponse("legacy-cart"), null,
                        NegotiatedCapabilities.none()));
        MerchantCartPluginDispatchService service = new MerchantCartPluginDispatchService(
                client, registry(), objectMapper, List.of(),
                new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        MerchantCartProvider provider = provider();
        CartRoutingTarget target = new CartRoutingTarget(
                "LEGACY:merchant:" + provider.merchantId(), MerchantIntegrationProvider.GENERIC_UCP,
                null, null, provider);

        UcpCartToolResult result = service.createCart(target, new CreateCartRequest(
                List.of(new CartAddItem("variant-1", 1)), null, List.of(), List.of(), List.of(),
                List.of(), List.of(), null), UcpSession.start());

        assertThat(result.response().cart().id()).isEqualTo("legacy-cart");
        verify(client, times(2)).callTool(any(MerchantCartProvider.class), eq("create_cart"), any(), any());
        verify(client, never()).callToolExactEndpoint(any(), any(), any(), any());
    }

    @Test
    void legacyUpdateFallbackRetainsFulfillmentMethodAndDestinationIdentity() {
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callTool(any(MerchantCartProvider.class), eq("update_cart"), any(), any()))
                .thenThrow(new MerchantMcpToolException("primary contract unsupported"))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://merchant.example/api/mcp", cartResponse("legacy-cart"), null,
                        NegotiatedCapabilities.none()));
        MerchantCartPluginDispatchService service = new MerchantCartPluginDispatchService(
                client, registry(), objectMapper, List.of(),
                new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        MerchantCartProvider provider = provider();
        CartRoutingTarget target = new CartRoutingTarget(
                "LEGACY:merchant:" + provider.merchantId(), MerchantIntegrationProvider.GENERIC_UCP,
                null, null, provider);
        CartDeliveryAddressSelection replacement = new CartDeliveryAddressSelection(
                "shipping-method", true,
                new CartDeliveryAddress(
                        "home", null, null, null, "1 Main St", null, "New York", "NY", "10001", "US"));

        UcpCartToolResult result = service.updateCart(target, new UpdateCartRequest(
                "cart-1", List.of(), List.of(), List.of(), null,
                List.of(), List.of(replacement), List.of(), null, null, null), UcpSession.start());

        assertThat(result.response().cart().id()).isEqualTo("legacy-cart");
        verify(client).callTool(
                eq(provider),
                eq("update_cart"),
                argThat(this::hasLegacyFulfillmentIdentity),
                any()
        );
    }

    private boolean hasLegacyFulfillmentIdentity(Object arguments) {
        try {
            JsonNode payload = objectMapper.valueToTree(arguments);
            JsonNode replacement = payload.path("delivery_addresses_to_replace").path(0);
            return replacement.path("method_id").asText().equals("shipping-method")
                    && replacement.path("id").asText().equals("home")
                    && replacement.path("delivery_address").path("zip").asText().equals("10001");
        } catch (Exception exception) {
            return false;
        }
    }

    private MerchantMcpToolClient merchantMcpToolClient(RestClient restClient) {
        return new MerchantMcpToolClient(
                restClient,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName("93.184.216.34")))
        );
    }

    private CapabilityRegistry registry() {
        return new CapabilityRegistry(List.of(
                new CreateCartCapability(objectMapper),
                new GetCartCapability(objectMapper),
                new UpdateCartCapability(objectMapper),
                new CancelCartCapability(objectMapper)
        ));
    }

    private MerchantCartProvider provider() {
        return new MerchantCartProvider(
                UUID.randomUUID(),
                "merchant.example",
                "https://merchant.example/api/mcp",
                null
        );
    }

    private String mcpResponse(String text) throws Exception {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": %s
                      }
                    ],
                    "structuredContent": {
                      "ucp": {
                        "capabilities": {
                          "dev.ucp.shopping.cart.create": "1.0.0",
                          "dev.ucp.shopping.cart.update": "1.0.0",
                          "dev.ucp.shopping.cart.get": "1.0.0",
                          "dev.ucp.shopping.cart.cancel": "1.0.0"
                        }
                      }
                    },
                    "isError": false
                  }
                }
                """.formatted(objectMapper.writeValueAsString(text));
    }

    private String cartResponse(String cartId) {
        return """
                {
                  "instructions": "Checkout when ready",
                  "cart": {
                    "id": "%s",
                    "created_at": "2026-06-16T11:05:00Z",
                    "updated_at": "2026-06-16T11:05:01Z",
                    "expires_at": "2026-06-16T12:05:00Z",
                    "continue_url": "https://merchant.example/continue",
                    "lines": [],
                    "cost": {
                      "total_amount": {"amount": "0.00", "currency": "USD"},
                      "subtotal_amount": {"amount": "0.00", "currency": "USD"}
                    },
                    "total_quantity": 0
                  },
                  "errors": []
                }
                """.formatted(cartId);
    }
}

package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.cart.cancel.CancelCartCapability;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.CreateCartCapability;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.GetCartCapability;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.UpdateCartCapability;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.support.UcpSession;
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
                objectMapper
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

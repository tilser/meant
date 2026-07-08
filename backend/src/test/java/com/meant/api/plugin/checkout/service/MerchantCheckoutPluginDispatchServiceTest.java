package com.meant.api.plugin.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.common.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantCheckoutPluginDispatchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesCartToCreateCheckoutAndStoresContinueUrlOnSession() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"create_checkout\"")))
                .andExpect(content().string(containsString("\"checkout\"")))
                .andExpect(content().string(containsString("\"line_items\"")))
                .andExpect(content().string(containsString("\"id\":\"gid://shopify/ProductVariant/1\"")))
                .andRespond(withSuccess(mcpResponse(checkoutResponse()), MediaType.APPLICATION_JSON));

        UcpCheckoutToolResult result = service.createCheckout(
                provider(),
                createCheckoutRequest(),
                session
        );

        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.response().resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(session.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(session.continueUrl()).isEqualTo("https://merchant.example/continue");
        server.verify();
    }

    @Test
    void createCheckoutAcceptsErrorMarkedRecoverableCheckoutAfterEndpointFallback() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"create_checkout\"")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 1,
                          "error": {
                            "code": -32602,
                            "message": "Invalid params"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://shopify.example/api/ucp/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"checkout\"")))
                .andExpect(content().string(containsString("\"line_items\"")))
                .andRespond(withSuccess(mcpResponse(recoverableRootCheckoutResponse(), true), MediaType.APPLICATION_JSON));

        UcpCheckoutToolResult result = service.createCheckout(
                provider(
                        "merchant.example",
                        "https://shopify.example/api/ucp/mcp",
                        "https://merchant.example/api/mcp"
                ),
                createCheckoutRequest(),
                session
        );

        assertThat(result.endpoint()).isEqualTo("https://shopify.example/api/ucp/mcp");
        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.response().messages()).hasSize(1);
        assertThat(result.response().messages().getFirst().isRecoverable()).isTrue();
        assertThat(session.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(session.continueUrl()).isEqualTo("https://merchant.example/continue");
        server.verify();
    }

    @Test
    void createCheckoutAcceptsBusinessErrorWhenCheckoutIsPresent() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"create_checkout\"")))
                .andRespond(withSuccess(mcpResponse(extensionInteractionErrorCheckoutResponse()), MediaType.APPLICATION_JSON));

        UcpCheckoutToolResult result = service.createCheckout(
                provider(),
                createCheckoutRequest(),
                session
        );

        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.response().errors())
                .extracting("message")
                .containsExactly("An extension interaction is required to complete the checkout.");
        assertThat(session.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        server.verify();
    }

    @Test
    void getCheckoutSendsIdArgument() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"get_checkout\"")))
                .andExpect(content().string(containsString("\"id\":\"gid://shopify/Checkout/1\"")))
                .andRespond(withSuccess(mcpResponse(checkoutResponse()), MediaType.APPLICATION_JSON));

        UcpCheckoutToolResult result = service.getCheckout(
                provider(),
                new GetCheckoutRequest("gid://shopify/Checkout/1"),
                session
        );

        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(session.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
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
                new CreateCheckoutCapability(objectMapper),
                new GetCheckoutCapability(objectMapper),
                new UpdateCheckoutCapability(objectMapper)
        ));
    }

    private MerchantCartProvider provider() {
        return provider("merchant.example", "https://merchant.example/api/mcp", null);
    }

    private MerchantCartProvider provider(String domain, String advertisedMcpEndpoint, String profileMcpEndpoint) {
        return new MerchantCartProvider(
                UUID.randomUUID(),
                domain,
                advertisedMcpEndpoint,
                profileMcpEndpoint
        );
    }

    private CreateCheckoutRequest createCheckoutRequest() {
        return new CreateCheckoutRequest(
                "gid://shopify/Cart/1",
                List.of(new CreateCheckoutRequest.LineItem(
                        "gid://shopify/CartLine/1",
                        "gid://shopify/ProductVariant/1",
                        1
                ))
        );
    }

    private String mcpResponse(String text) throws Exception {
        return mcpResponse(text, false);
    }

    private String mcpResponse(String text, boolean isError) throws Exception {
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
                          "dev.ucp.shopping.checkout.create": "1.0.0",
                          "dev.ucp.shopping.checkout.get": "1.0.0",
                          "dev.ucp.shopping.checkout.update": "1.0.0"
                        }
                      }
                    },
                    "isError": %s
                  }
                }
                """.formatted(objectMapper.writeValueAsString(text), isError);
    }

    private String checkoutResponse() {
        return """
                {
                  "instructions": "Open checkout in browser",
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "open",
                    "checkout_url": "https://merchant.example/checkout",
                    "continue_url": "https://merchant.example/continue",
                    "messages": []
                  },
                  "errors": []
                }
                """;
    }

    private String recoverableRootCheckoutResponse() {
        return """
                {
                  "id": "gid://shopify/Checkout/1",
                  "cart_id": "gid://shopify/Cart/1",
                  "status": "incomplete",
                  "checkout_url": "https://merchant.example/checkout",
                  "continue_url": "https://merchant.example/continue",
                  "messages": [
                    {
                      "type": "error",
                      "code": "delivery_address_required",
                      "severity": "recoverable",
                      "content": "A destination address is required in order to continue."
                    }
                  ],
                  "errors": []
                }
                """;
    }

    private String extensionInteractionErrorCheckoutResponse() {
        return """
                {
                  "checkout": {
                    "id": "gid://shopify/Checkout/1",
                    "cart_id": "gid://shopify/Cart/1",
                    "status": "incomplete",
                    "checkout_url": "https://merchant.example/checkout",
                    "messages": []
                  },
                  "errors": [
                    {
                      "code": "extension_interaction_required",
                      "message": "An extension interaction is required to complete the checkout."
                    }
                  ]
                }
                """;
    }
}

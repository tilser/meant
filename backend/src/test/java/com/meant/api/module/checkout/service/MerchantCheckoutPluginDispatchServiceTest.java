package com.meant.api.module.checkout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.plugin.checkout.create.CreateCheckoutCapability;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.GetCheckoutCapability;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.UpdateCheckoutCapability;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.transport.profile.AgentAttributionProperties;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantCheckoutPluginDispatchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentAttributionProperties attributionProperties = new AgentAttributionProperties(
            "app.usemeant.com", "meant", "agentic_commerce");

    @Test
    void dispatchesCartToCreateCheckoutAndStoresContinueUrlOnSession() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper,
                List.of()
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
                objectMapper,
                List.of()
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://shopify.example/api/ucp/mcp"))
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
        server.expect(requestTo("https://merchant.example/api/ucp/mcp"))
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

        assertThat(result.endpoint()).isEqualTo("https://merchant.example/api/ucp/mcp");
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
                objectMapper,
                List.of()
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
    void sanitizesTechnicalEndpointInRejectedCheckoutError() {
        String endpoint = "https://weareallbirds.myshopify.com/api/ucp/mcp";
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callToolReturningJsonToolErrors(
                any(MerchantCartProvider.class),
                eq("create_checkout"),
                any()
        )).thenReturn(new MerchantMcpToolCallResult(
                endpoint,
                """
                        {
                          "messages": [],
                          "errors": [
                            {
                              "code": "checkout_rejected",
                              "message": "Checkout rejected by %s"
                            }
                          ]
                        }
                        """.formatted(endpoint),
                null,
                NegotiatedCapabilities.none()
        ));
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                client,
                registry(),
                objectMapper,
                List.of()
        );
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(),
                "allbirds.com",
                "weareallbirds.myshopify.com",
                endpoint,
                null,
                List.of(),
                com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy.unavailable(),
                null,
                Set.of("dev.ucp.shopping.checkout")
        );

        assertThatThrownBy(() -> service.createCheckout(
                provider,
                createCheckoutRequest(),
                UcpSession.start()
        ))
                .isInstanceOf(CartException.class)
                .hasMessage("Checkout rejected by allbirds.com")
                .satisfies(exception -> assertThat(((CartException) exception).getSafeMessage())
                        .doesNotContain("myshopify.com", "/api/ucp/mcp"));
    }

    @Test
    void createCheckoutAcceptsRequiresBuyerInputMessageWhenCheckoutIsPresent() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                registry(),
                objectMapper,
                List.of()
        );
        UcpSession session = UcpSession.cart("gid://shopify/Cart/1", null, null);

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"create_checkout\"")))
                .andRespond(withSuccess(
                        mcpResponse(requiresBuyerInputRootCheckoutResponse(), true),
                        MediaType.APPLICATION_JSON
                ));

        UcpCheckoutToolResult result = service.createCheckout(
                provider(),
                createCheckoutRequest(),
                session
        );

        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.response().resolvedCheckout().status()).isEqualTo("requires_escalation");
        assertThat(result.response().messages())
                .extracting(UcpCheckoutResponse.CheckoutMessage::code)
                .containsExactly("extension_interaction_required", "delivery_address_required");
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
                objectMapper,
                List.of()
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

    @Test
    void boundUpdateFailureUsesOneExactEndpointAndNeverFallsBackToCandidates() {
        MerchantMcpToolClient client = mock(MerchantMcpToolClient.class);
        when(client.callToolExactEndpointReturningJsonToolErrors(any(), any(), any(), any()))
                .thenThrow(new MerchantMcpToolException("ambiguous timeout"));
        MerchantCheckoutPluginDispatchService service = new MerchantCheckoutPluginDispatchService(
                client,
                registry(),
                objectMapper,
                List.of()
        );
        MerchantCartProvider provider = provider();
        CartRoutingTarget target = new CartRoutingTarget(
                "GENERIC_UCP:integration:" + UUID.randomUUID(),
                MerchantIntegrationProvider.GENERIC_UCP,
                UUID.randomUUID(),
                null,
                provider
        );
        UpdateCheckoutRequest request = new UpdateCheckoutRequest(
                "gid://shopify/Checkout/1",
                List.of(new UpdateCheckoutRequest.LineItem(
                        "gid://shopify/CheckoutLine/1",
                        "gid://shopify/ProductVariant/1",
                        1
                )),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null
        );

        assertThatThrownBy(() -> service.updateCheckout(target, request, UcpSession.start()))
                .isInstanceOf(MerchantMcpToolException.class)
                .hasMessageContaining("ambiguous timeout");

        verify(client).callToolExactEndpointReturningJsonToolErrors(
                eq(provider), eq("update_checkout"), any(), eq(java.util.Map.of()));
        verify(client, never()).callToolReturningJsonToolErrors(
                any(MerchantCartProvider.class), any(), any());
    }

    private MerchantMcpToolClient merchantMcpToolClient(RestClient restClient) {
        return new MerchantMcpToolClient(
                restClient,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName("93.184.216.34")))
        );
    }

    private CapabilityRegistry registry() {
        return new CapabilityRegistry(List.of(
                new CreateCheckoutCapability(objectMapper, attributionProperties),
                new GetCheckoutCapability(objectMapper),
                new UpdateCheckoutCapability(objectMapper, attributionProperties)
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

    private String requiresBuyerInputRootCheckoutResponse() {
        return """
                {
                  "id": "gid://shopify/Checkout/1",
                  "currency": "USD",
                  "status": "requires_escalation",
                  "continue_url": "https://merchant.example/continue",
                  "messages": [
                    {
                      "type": "error",
                      "content_type": "plain",
                      "code": "extension_interaction_required",
                      "content": "An extension interaction is required to complete the checkout.",
                      "severity": "requires_buyer_input"
                    },
                    {
                      "type": "error",
                      "content_type": "plain",
                      "code": "delivery_address_required",
                      "content": "A destination address is required in order to continue.",
                      "severity": "recoverable"
                    }
                  ]
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

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
                .andExpect(content().string(containsString("\"cart_id\":\"gid://shopify/Cart/1\"")))
                .andRespond(withSuccess(mcpResponse(checkoutResponse()), MediaType.APPLICATION_JSON));

        UcpCheckoutToolResult result = service.createCheckout(
                provider(),
                new CreateCheckoutRequest("gid://shopify/Cart/1"),
                session
        );

        assertThat(result.response().resolvedCheckout().id()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.response().resolvedCheckout().continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(session.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(session.continueUrl()).isEqualTo("https://merchant.example/continue");
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
                          "dev.ucp.shopping.checkout.create": "1.0.0",
                          "dev.ucp.shopping.checkout.get": "1.0.0",
                          "dev.ucp.shopping.checkout.update": "1.0.0"
                        }
                      }
                    },
                    "isError": false
                  }
                }
                """.formatted(objectMapper.writeValueAsString(text));
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
}

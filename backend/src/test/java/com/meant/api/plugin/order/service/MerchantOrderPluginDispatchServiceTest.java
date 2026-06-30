package com.meant.api.plugin.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.MerchantMcpToolClient;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.plugin.order.common.dto.UcpOrderToolResult;
import com.meant.api.plugin.order.common.service.MerchantOrderPluginDispatchService;
import com.meant.api.plugin.order.get.GetOrderCapability;
import com.meant.api.plugin.order.get.dto.GetOrderRequest;
import com.meant.api.plugin.support.UcpSession;
import com.meant.api.plugin.transport.registry.CapabilityRegistry;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantOrderPluginDispatchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesGetOrderWithBearerTokenAndParsesResponse() throws Exception {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantOrderPluginDispatchService service = new MerchantOrderPluginDispatchService(
                merchantMcpToolClient(restClientBuilder.build()),
                new CapabilityRegistry(List.of(new GetOrderCapability(objectMapper))),
                objectMapper
        );

        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer order-token"))
                .andExpect(content().string(containsString("\"name\":\"get_order\"")))
                .andExpect(content().string(containsString("\"order_id\":\"gid://shopify/Order/1\"")))
                .andRespond(withSuccess(mcpResponse(orderResponse()), MediaType.APPLICATION_JSON));

        UcpOrderToolResult result = service.getOrder(
                provider(),
                new GetOrderRequest("gid://shopify/Order/1"),
                UcpSession.start(),
                "Bearer",
                "order-token"
        );

        assertThat(result.endpoint()).isEqualTo("https://merchant.example/api/mcp");
        assertThat(result.response().order().id()).isEqualTo("gid://shopify/Order/1");
        assertThat(result.response().order().lineItems()).hasSize(1);
        server.verify();
    }

    private MerchantMcpToolClient merchantMcpToolClient(RestClient restClient) {
        return new MerchantMcpToolClient(
                restClient,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getByName("93.184.216.34")))
        );
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
                          "dev.ucp.shopping.order.get": "1.0.0"
                        }
                      }
                    },
                    "isError": false
                  }
                }
                """.formatted(objectMapper.writeValueAsString(text));
    }

    private String orderResponse() {
        return """
                {
                  "order": {
                    "id": "gid://shopify/Order/1",
                    "name": "#1001",
                    "financial_status": "paid",
                    "fulfillment_status": "in_transit",
                    "line_items": [
                      {
                        "id": "gid://shopify/LineItem/1",
                        "title": "Candle",
                        "quantity": 1,
                        "price": "14.95",
                        "total_price": "14.95",
                        "currency": "USD"
                      }
                    ]
                  },
                  "errors": []
                }
                """;
    }
}

package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MerchantMcpToolClientTest {

    @Test
    void usesAbsoluteEndpointWhenDomainIsMissing() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantMcpToolClient client = new MerchantMcpToolClient(restClientBuilder.build());
        server.expect(requestTo("https://advertised.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"ok\\":true}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        MerchantMcpToolCallResult result = client.callTool(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        null,
                        "Merchant",
                        "https://advertised.example/api/mcp",
                        "/relative/mcp",
                        "Context",
                        0.9d,
                        0.8d,
                        1
                ),
                "search_catalog",
                Map.of("catalog", Map.of("query", "candle"))
        );

        assertThat(result.endpoint()).isEqualTo("https://advertised.example/api/mcp");
        assertThat(result.contentText()).isEqualTo("{\"ok\":true}");
        server.verify();
    }
}

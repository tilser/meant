package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantCatalogSearchClientTest {

    @Test
    void searchesProfileMcpEndpointAndDoesNotCallAdvertisedMcpEndpoint() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCatalogSearchClient client = new MerchantCatalogSearchClient(
                new MerchantMcpToolClient(restClientBuilder.build()),
                new ObjectMapper()
        );
        server.expect(requestTo("https://profile.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"catalog\"")))
                .andExpect(content().string(containsString("\"limit\":10")))
                .andExpect(content().string(not(containsString("\"cursor\""))))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"products\\":[{\\"id\\":\\"product-1\\",\\"title\\":\\"Trail Running Shoe\\"}]}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CatalogSearchResult result = client.searchCatalog(
                new MerchantSemanticSearchResult(
                        UUID.randomUUID(),
                        "merchant.example",
                        "Merchant",
                        "https://advertised.example/api/ucp/mcp",
                        "https://profile.example/api/mcp",
                        "Categories: Shoes",
                        0.9d,
                        0.8d,
                        1
                ),
                "running shoes",
                10
        );

        assertThat(result.endpoint()).isEqualTo("https://profile.example/api/mcp");
        assertThat(result.products()).extracting("id").containsExactly("product-1");
        server.verify();
    }
}

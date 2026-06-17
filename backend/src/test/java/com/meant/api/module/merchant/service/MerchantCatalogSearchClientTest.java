package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.CatalogSearchFilters;
import com.meant.api.module.merchant.service.dto.CatalogSearchPriceFilter;
import com.meant.api.module.merchant.service.dto.CatalogSearchResult;
import com.meant.api.module.merchant.service.dto.CatalogSearchSignals;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import java.util.List;
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

    @Test
    void sendsCatalogContextAndFilters() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCatalogSearchClient client = new MerchantCatalogSearchClient(
                new MerchantMcpToolClient(restClientBuilder.build()),
                new ObjectMapper()
        );
        server.expect(requestTo("https://profile.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"query\":\"throw pillow\"")))
                .andExpect(content().string(containsString("\"context\"")))
                .andExpect(content().string(containsString("\"address_country\":\"US\"")))
                .andExpect(content().string(containsString("\"language\":\"en\"")))
                .andExpect(content().string(containsString("\"currency\":\"USD\"")))
                .andExpect(content().string(containsString("\"intent\":\"Original request: pillow under 100 USD\"")))
                .andExpect(content().string(containsString("\"signals\"")))
                .andExpect(content().string(containsString("\"dev.ucp.buyer_ip\":\"203.0.113.4\"")))
                .andExpect(content().string(containsString("\"dev.ucp.user_agent\":\"Meant Test\"")))
                .andExpect(content().string(containsString("\"filters\"")))
                .andExpect(content().string(containsString("\"price\"")))
                .andExpect(content().string(containsString("\"max\":10000")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"products\\":[{\\"id\\":\\"product-1\\",\\"title\\":\\"Throw Pillow\\"}]}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        CatalogSearchResult result = client.searchCatalog(
                merchant(),
                "throw pillow",
                new CatalogSearchContext(
                        "US",
                        null,
                        null,
                        "en",
                        "USD",
                        "Original request: pillow under 100 USD"
                ),
                new CatalogSearchSignals("203.0.113.4", "Meant Test"),
                new CatalogSearchFilters(List.of(), new CatalogSearchPriceFilter(null, 10000L)),
                10
        );

        assertThat(result.products()).extracting("id").containsExactly("product-1");
        server.verify();
    }

    private MerchantSemanticSearchResult merchant() {
        return new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://advertised.example/api/ucp/mcp",
                "https://profile.example/api/mcp",
                "Categories: Shoes",
                0.9d,
                0.8d,
                1
        );
    }
}

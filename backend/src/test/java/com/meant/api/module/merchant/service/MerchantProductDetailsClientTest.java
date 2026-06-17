package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.service.dto.CatalogSearchContext;
import com.meant.api.module.merchant.service.dto.MerchantSemanticSearchResult;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantProductDetailsClientTest {

    @Test
    void sendsProductDetailsRequestAndParsesSelectedVariant() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantProductDetailsClient client = new MerchantProductDetailsClient(
                new MerchantMcpToolClient(restClientBuilder.build()),
                new ObjectMapper()
        );
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"get_product_details\"")))
                .andExpect(content().string(containsString("\"product_id\":\"gid://shopify/Product/1\"")))
                .andExpect(content().string(containsString("\"country\":\"US\"")))
                .andExpect(content().string(containsString("\"language\":\"en\"")))
                .andRespond(withSuccess("""
                        {
                          "jsonrpc": "2.0",
                          "id": 4,
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "{\\"product\\":{\\"product_id\\":\\"gid://shopify/Product/1\\",\\"title\\":\\"Candle\\",\\"price_range\\":{\\"min\\":\\"12.95\\",\\"max\\":\\"14.95\\",\\"currency\\":\\"USD\\"},\\"selectedOrFirstAvailableVariant\\":{\\"variant_id\\":\\"gid://shopify/ProductVariant/1\\",\\"title\\":\\"3x6\\",\\"price\\":\\"14.95\\",\\"currency\\":\\"USD\\",\\"available\\":true,\\"selected_options\\":[{\\"name\\":\\"Size\\",\\"value\\":\\"3x6\\"}]}}}"
                              }
                            ],
                            "isError": false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ProductDetailsResult result = client.getProductDetails(
                merchant(),
                "gid://shopify/Product/1",
                new CatalogSearchContext("US", null, null, "en", "USD", "Original request")
        );

        assertThat(result.product().productId()).isEqualTo("gid://shopify/Product/1");
        assertThat(result.product().selectedOrFirstAvailableVariant().variantId())
                .isEqualTo("gid://shopify/ProductVariant/1");
        assertThat(result.product().selectedOrFirstAvailableVariant().selectedOptions())
                .extracting("value")
                .containsExactly("3x6");
        server.verify();
    }

    private MerchantSemanticSearchResult merchant() {
        return new MerchantSemanticSearchResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                "https://merchant.example/api/mcp",
                null,
                "Merchant",
                0.9d,
                0.8d,
                1
        );
    }
}

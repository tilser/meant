package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.service.dto.CartAddItem;
import com.meant.api.module.merchant.service.dto.CartToolResult;
import com.meant.api.module.merchant.service.dto.CartUpdateItem;
import com.meant.api.module.merchant.service.dto.UpdateCartArguments;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MerchantCartClientTest {

    @Test
    void createsCartWithAddItemsAndParsesCheckoutUrl() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCartClient client = new MerchantCartClient(new MerchantMcpToolClient(restClientBuilder), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"update_cart\"")))
                .andExpect(content().string(not(containsString("\"cart_id\""))))
                .andExpect(content().string(containsString("\"product_variant_id\":\"gid://shopify/ProductVariant/1\"")))
                .andRespond(withSuccess(cartResponse(), MediaType.APPLICATION_JSON));

        CartToolResult result = client.updateCart(
                merchant(),
                new UpdateCartArguments(
                        null,
                        List.of(new CartAddItem("gid://shopify/ProductVariant/1", 1)),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                )
        );

        assertThat(result.response().cart().checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(result.response().cart().lines()).hasSize(1);
        assertThat(result.response().cart().lines().getFirst().merchandise().id())
                .isEqualTo("gid://shopify/ProductVariant/1");
        server.verify();
    }

    @Test
    void updatesAndRemovesCartLines() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCartClient client = new MerchantCartClient(new MerchantMcpToolClient(restClientBuilder), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"cart_id\":\"gid://shopify/Cart/1\"")))
                .andExpect(content().string(containsString("\"update_items\"")))
                .andExpect(content().string(containsString("\"remove_line_ids\"")))
                .andRespond(withSuccess(cartResponse(), MediaType.APPLICATION_JSON));

        client.updateCart(
                merchant(),
                new UpdateCartArguments(
                        "gid://shopify/Cart/1",
                        List.of(),
                        List.of(new CartUpdateItem("gid://shopify/CartLine/1", 2)),
                        List.of("gid://shopify/CartLine/2"),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                )
        );

        server.verify();
    }

    @Test
    void getsCartByRemoteCartId() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MerchantCartClient client = new MerchantCartClient(new MerchantMcpToolClient(restClientBuilder), new ObjectMapper());
        server.expect(requestTo("https://merchant.example/api/mcp"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"name\":\"get_cart\"")))
                .andExpect(content().string(containsString("\"cart_id\":\"gid://shopify/Cart/1\"")))
                .andRespond(withSuccess(cartResponse(), MediaType.APPLICATION_JSON));

        CartToolResult result = client.getCart(merchant(), "gid://shopify/Cart/1");

        assertThat(result.response().cart().id()).isEqualTo("gid://shopify/Cart/1");
        server.verify();
    }

    private Merchant merchant() {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain("merchant.example")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("hash")
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private String cartResponse() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 4,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "{\\"instructions\\":\\"Checkout when ready\\",\\"cart\\":{\\"id\\":\\"gid://shopify/Cart/1\\",\\"created_at\\":\\"2026-06-16T11:05:00.857Z\\",\\"updated_at\\":\\"2026-06-16T11:05:00.857Z\\",\\"lines\\":[{\\"id\\":\\"gid://shopify/CartLine/1\\",\\"quantity\\":1,\\"cost\\":{\\"total_amount\\":{\\"amount\\":\\"14.95\\",\\"currency\\":\\"USD\\"},\\"subtotal_amount\\":{\\"amount\\":\\"14.95\\",\\"currency\\":\\"USD\\"}},\\"merchandise\\":{\\"id\\":\\"gid://shopify/ProductVariant/1\\",\\"title\\":\\"3x6\\",\\"product\\":{\\"id\\":\\"gid://shopify/Product/1\\",\\"title\\":\\"Candle\\"}}}],\\"cost\\":{\\"total_amount\\":{\\"amount\\":\\"14.95\\",\\"currency\\":\\"USD\\"},\\"subtotal_amount\\":{\\"amount\\":\\"14.95\\",\\"currency\\":\\"USD\\"}},\\"total_quantity\\":1,\\"checkout_url\\":\\"https://merchant.example/checkout\\"},\\"errors\\":[]}"
                      }
                    ],
                    "isError": false
                  }
                }
                """;
    }
}

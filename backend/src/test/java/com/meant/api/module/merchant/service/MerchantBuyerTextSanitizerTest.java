package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MerchantBuyerTextSanitizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void neutralizesGenericTransportCoordinatesWithoutCollapsingOrdinaryProseOrProductUrls() {
        String sanitized = MerchantBuyerTextSanitizer.sanitize(
                "I need details before I continue with manning-shoes.myshopify.com. "
                        + "Retry mcp.shop.example or https://catalog.transport.test/api/ucp/mcp. "
                        + "The official product is https://manning.com/products/lethal-speed."
        );

        assertThat(sanitized)
                .isEqualTo(
                        "I need details before I continue with the merchant. "
                                + "Retry the merchant or the merchant. "
                                + "The official product is https://manning.com/products/lethal-speed."
                )
                .doesNotContain("myshopify.com", "mcp.shop.example", "/api/ucp/mcp");
    }

    @Test
    void preservesAnOrdinaryPathThatOnlyStartsWithTheMcpLetters() {
        assertThat(MerchantBuyerTextSanitizer.sanitize(
                "Browse https://official.example/mcpology for the product."
        )).isEqualTo("Browse https://official.example/mcpology for the product.");
    }

    @Test
    void neutralizesStandaloneProtocolPaths() {
        assertThat(MerchantBuyerTextSanitizer.sanitize(
                "Internal paths: /.well-known/ucp.json, /api/mcp, and /mcp/session/1."
        )).isEqualTo("Internal paths: the merchant, the merchant, and the merchant.");
    }

    @Test
    void sanitizesEveryNestedJsonStringWhilePreservingShapeAndOrdinaryUrls() throws Exception {
        String sanitized = MerchantBuyerTextSanitizer.sanitizeJson("""
                {
                  "text": "Continue at https://seller.myshopify.com/mcp.",
                  "nested": {
                    "messages": [
                      "Ask api.mcp.shop.example for help.",
                      "Browse https://official.example/products/shoe"
                    ],
                    "count": 2,
                    "available": true
                  }
                }
                """);

        JsonNode root = objectMapper.readTree(sanitized);

        assertThat(root.path("text").asText()).isEqualTo("Continue at the merchant.");
        assertThat(root.path("nested").path("messages").get(0).asText())
                .isEqualTo("Ask the merchant for help.");
        assertThat(root.path("nested").path("messages").get(1).asText())
                .isEqualTo("Browse https://official.example/products/shoe");
        assertThat(root.path("nested").path("count").asInt()).isEqualTo(2);
        assertThat(root.path("nested").path("available").asBoolean()).isTrue();
    }

    @Test
    void preservesAnExplicitlyVerifiedMyshopifyStorefrontOrigin() {
        String sanitized = MerchantBuyerTextSanitizer.sanitize(
                "Continue at https://seller.myshopify.com/api/mcp",
                "seller.myshopify.com",
                "seller.myshopify.com",
                "https://seller.myshopify.com/api/mcp"
        );

        assertThat(sanitized).isEqualTo("Continue at seller.myshopify.com");
    }
}

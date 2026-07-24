package com.meant.api.module.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.service.BuyerSafeRoutingScopeKey;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AgentBuyerPayloadSanitizerTest {

    private static final String TECHNICAL_SCOPE =
            "SHOPIFY:merchant:gid://shopify/Shop/1:domain:seller.myshopify.com";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recursivelyProjectsRoutingScopesDropsCoordinatesAndSanitizesBuyerText()
            throws Exception {
        String sanitized = AgentBuyerPayloadSanitizer.sanitize("""
                {
                  "routingScopeKey": "SHOPIFY:merchant:gid://shopify/Shop/1:domain:seller.myshopify.com",
                  "endpoint": "https://mcp.shop.example",
                  "message": "Continue with seller.myshopify.com.",
                  "officialUrl": "https://official.example/products/shoe",
                  "nested": [{
                    "advertisedMcpEndpoint": "https://mcp.shop.example",
                    "profileMcpEndpoint": "https://mcp.shop.example/profile",
                    "ucpUrl": "https://transport.example/.well-known/ucp",
                    "profileEndpoint": "https://transport.example/profile",
                    "routingDomain": "opaque-routing.internal.example",
                    "ExternalMerchantDomain": "legacy-routing.internal.example",
                    "uri": "https://transport.example/mcp",
                    "routingScopeKey": "SHOPIFY:merchant:gid://shopify/Shop/1:domain:seller.myshopify.com",
                    "summary": "Retry https://transport.example/api/ucp/mcp/session/1."
                  }],
                  "count": 2,
                  "available": true
                }
                """);

        JsonNode root = objectMapper.readTree(sanitized);
        JsonNode nested = root.path("nested").get(0);

        assertThat(root.path("routingScopeKey").asText())
                .isEqualTo(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE));
        assertThat(root.has("endpoint")).isFalse();
        assertThat(root.path("message").asText()).isEqualTo("Continue with the merchant.");
        assertThat(root.path("officialUrl").asText())
                .isEqualTo("https://official.example/products/shoe");
        assertThat(nested.has("advertisedMcpEndpoint")).isFalse();
        assertThat(nested.has("profileMcpEndpoint")).isFalse();
        assertThat(nested.has("ucpUrl")).isFalse();
        assertThat(nested.has("profileEndpoint")).isFalse();
        assertThat(nested.has("routingDomain")).isFalse();
        assertThat(nested.has("ExternalMerchantDomain")).isFalse();
        assertThat(nested.has("uri")).isFalse();
        assertThat(nested.path("routingScopeKey").asText())
                .isEqualTo(BuyerSafeRoutingScopeKey.project(TECHNICAL_SCOPE));
        assertThat(nested.path("summary").asText()).isEqualTo("Retry the merchant.");
        assertThat(root.path("count").asInt()).isEqualTo(2);
        assertThat(root.path("available").asBoolean()).isTrue();
    }

    @Test
    void usesDroppedEndpointCoordinatesToSanitizeSiblingUrlsAndEmbeddedLegacyJson()
            throws Exception {
        String sanitized = AgentBuyerPayloadSanitizer.sanitize("""
                {
                  "merchantOrigin": "nycfactory.com",
                  "endpoint": "https://transport.vendor.example/custom",
                  "imageUrl": "https://transport.vendor.example/custom/image.png",
                  "payloadJson": "{\\"resultJson\\":\\"{\\\\\\"endpoint\\\\\\":\\\\\\"https://legacy.vendor.example/private\\\\\\",\\\\\\"message\\\\\\":\\\\\\"Use https://legacy.vendor.example/private/session\\\\\\"}\\",\\"label\\":\\"Open https://transport.vendor.example/custom\\"}"
                }
                """);

        JsonNode root = objectMapper.readTree(sanitized);
        JsonNode payload = objectMapper.readTree(root.path("payloadJson").asText());
        JsonNode result = objectMapper.readTree(payload.path("resultJson").asText());

        assertThat(root.has("endpoint")).isFalse();
        assertThat(root.path("imageUrl").asText()).isEqualTo("nycfactory.com/image.png");
        assertThat(payload.path("label").asText()).isEqualTo("Open nycfactory.com");
        assertThat(result.has("endpoint")).isFalse();
        assertThat(result.path("message").asText()).isEqualTo("Use nycfactory.com/session");
        assertThat(sanitized)
                .doesNotContain("transport.vendor.example", "legacy.vendor.example");
    }
}

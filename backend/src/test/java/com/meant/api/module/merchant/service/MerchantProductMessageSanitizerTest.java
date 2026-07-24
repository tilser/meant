package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantProductMessageSanitizerTest {

    private static final String UCP_URL = "https://profile-source.transport.test/custom-ucp.json";
    private static final String ADVERTISED = "https://advertised.transport.test/custom-mcp";
    private static final String PROFILE_MCP = "https://profile-mcp.transport.test/legacy-mcp";
    private static final String PROFILE_ENDPOINT =
            "https://fetched-profile.transport.test/redirected-ucp.json";
    private static final String INTEGRATION = "https://integration.transport.test/catalog-mcp";
    private static final String RUNTIME = "https://runtime.transport.test/api/ucp/mcp";
    private static final List<String> PERSISTED_ALIASES =
            List.of(UCP_URL, ADVERTISED, PROFILE_MCP, PROFILE_ENDPOINT, INTEGRATION);
    private static final List<String> ALL_COORDINATES =
            List.of(UCP_URL, ADVERTISED, PROFILE_MCP, PROFILE_ENDPOINT, INTEGRATION, RUNTIME);

    @Test
    void sanitizesEveryBuyerVisibleFieldAcrossDistinctTransportCoordinates() {
        MerchantProductMessageSanitizer.SanitizedMessage message =
                MerchantProductMessageSanitizer.sanitize(
                        RUNTIME,
                        ADVERTISED,
                        "/mcp",
                        PROFILE_MCP,
                        "Use " + String.join(", ", ALL_COORDINATES)
                                + ". Product: https://merchant.example/products/shoe",
                        RUNTIME,
                        PROFILE_ENDPOINT,
                        PROFILE_ENDPOINT + "/assets/private.png",
                        INTEGRATION + "/internal",
                        productDetailsContext()
                );

        assertThat(message.type()).isEqualTo("notice");
        assertThat(message.code()).isNull();
        assertThat(message.path()).isNull();
        assertThat(message.contentType()).isEqualTo("text/plain");
        assertThat(message.severity()).isNull();
        assertThat(message.presentation()).isEqualTo("inline");
        assertThat(message.imageUrl()).isNull();
        assertThat(message.url()).isNull();
        assertThat(message.content())
                .contains("merchant.example", "https://merchant.example/products/shoe")
                .doesNotContain("profile-source.transport.test")
                .doesNotContain("advertised.transport.test")
                .doesNotContain("profile-mcp.transport.test")
                .doesNotContain("fetched-profile.transport.test")
                .doesNotContain("integration.transport.test")
                .doesNotContain("runtime.transport.test");
    }

    @Test
    void preservesNormalizedMetadataAndLegitimateHttpsResources() {
        MerchantProductMessageSanitizer.SanitizedMessage message =
                MerchantProductMessageSanitizer.sanitize(
                        "INFO",
                        "FIT_NOTE",
                        "/variants/variant-1",
                        "text/markdown",
                        "True to size",
                        "INFO",
                        "DISCLOSURE",
                        "https://cdn.example/images/fit.png",
                        "https://merchant.example/products/shoe",
                        productDetailsContext()
                );

        assertThat(message).isEqualTo(new MerchantProductMessageSanitizer.SanitizedMessage(
                "info",
                "FIT_NOTE",
                "/variants/variant-1",
                "text/markdown",
                "True to size",
                "info",
                "disclosure",
                "https://cdn.example/images/fit.png",
                "https://merchant.example/products/shoe"
        ));
    }

    @Test
    void rejectsLinksAndImagesOnEveryKnownTransportCoordinate() {
        MerchantProductMessageSanitizer.TransportContext context = productDetailsContext();

        for (String endpoint : ALL_COORDINATES) {
            MerchantProductMessageSanitizer.SanitizedMessage message =
                    MerchantProductMessageSanitizer.sanitize(
                            "info",
                            "TRANSPORT_NOTE",
                            "/product",
                            "text/plain",
                            "Provider notice",
                            "info",
                            "inline",
                            endpoint + "/private.png",
                            endpoint,
                            context
                    );

            assertThat(message.imageUrl()).isNull();
            assertThat(message.url()).isNull();
        }
    }

    @Test
    void rejectsUnverifiedShopifyAliasesAndNonHttpsLinks() {
        MerchantProductMessageSanitizer.TransportContext context =
                MerchantProductMessageSanitizer.context(
                        null,
                        "https://catalog.transport.test/mcp",
                        "https://catalog.transport.test/mcp",
                        "seller.myshopify.com"
                );

        MerchantProductMessageSanitizer.SanitizedMessage message =
                MerchantProductMessageSanitizer.sanitize(
                        "info",
                        "SHOP_NOTE",
                        "/product",
                        "text/plain",
                        "Contact seller.myshopify.com",
                        "info",
                        "inline",
                        "https://seller.myshopify.com/product.png",
                        "http://merchant.example/products/shoe",
                        context
                );

        assertThat(message.content()).isEqualTo("Contact the merchant");
        assertThat(message.imageUrl()).isNull();
        assertThat(message.url()).isNull();
    }

    @Test
    void rejectsEveryKnownProtocolPathInMetadataAndLinks() {
        for (String protocolPath : List.of(
                "/.well-known/ucp.json",
                "/.well-known/ucp",
                "/api/ucp/mcp",
                "/api/mcp",
                "/mcp"
        )) {
            MerchantProductMessageSanitizer.SanitizedMessage message =
                    MerchantProductMessageSanitizer.sanitize(
                            "info",
                            "PROTOCOL_NOTE",
                            protocolPath,
                            "text/plain",
                            "Provider notice",
                            "info",
                            "inline",
                            "https://cdn.example" + protocolPath,
                            "https://merchant.example" + protocolPath,
                            productDetailsContext()
                    );

            assertThat(message.path()).isNull();
            assertThat(message.imageUrl()).isNull();
            assertThat(message.url()).isNull();
        }
    }

    private MerchantProductMessageSanitizer.TransportContext productDetailsContext() {
        ProductDetailsResult result = new ProductDetailsResult(RUNTIME, "redacted", null)
                .withBuyerContext("merchant.example", PERSISTED_ALIASES);
        return MerchantProductMessageSanitizer.context(result);
    }
}

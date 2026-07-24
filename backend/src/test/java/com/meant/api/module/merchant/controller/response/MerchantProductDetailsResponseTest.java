package com.meant.api.module.merchant.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MerchantProductDetailsResponseTest {

    private static final String MERCHANT_ORIGIN = "merchant.example";
    private static final String TECHNICAL_ENDPOINT =
            "https://catalog-transport.internal.test/custom-mcp";

    @Test
    void sanitizesProviderMessagesEvenWhenProductDetailsAreUnavailable() {
        String ucpUrl = "https://profile-source.transport.test/custom-ucp.json";
        String advertised = "https://advertised.transport.test/custom-mcp";
        String profileMcp = "https://profile-mcp.transport.test/legacy-mcp";
        String profileEndpoint = "https://fetched-profile.transport.test/redirected-ucp.json";
        String integration = "https://integration.transport.test/catalog-mcp";
        String runtime = "https://runtime.transport.test/api/ucp/mcp";
        ProductDetailsResponse.Message technical = new ProductDetailsResponse.Message(
                runtime,
                advertised,
                "/mcp",
                profileMcp,
                "Retry " + String.join(", ", ucpUrl, advertised, profileMcp, profileEndpoint, integration, runtime),
                runtime,
                profileEndpoint,
                profileEndpoint + "/private.png",
                integration + "/internal"
        );
        ProductDetailsResponse.Message legitimate = new ProductDetailsResponse.Message(
                "INFO",
                "CARE_GUIDE",
                "/product/care",
                "text/plain",
                "Read the care guide",
                "INFO",
                "INLINE",
                "https://cdn.example/care.png",
                "https://merchant.example/products/shoe"
        );
        ProductDetailsResult result = new ProductDetailsResult(
                runtime,
                "redacted",
                null,
                List.of(technical, legitimate),
                NegotiatedCapabilities.none(),
                "merchant.example",
                List.of(ucpUrl, advertised, profileMcp, profileEndpoint, integration)
        );

        MerchantProductDetailsResponse response = MerchantProductDetailsResponse.from(result);

        assertThat(response.messages()).hasSize(2);
        assertThat(response.messages().getFirst()).satisfies(message -> {
            assertThat(message.type()).isEqualTo("notice");
            assertThat(message.code()).isNull();
            assertThat(message.path()).isNull();
            assertThat(message.contentType()).isEqualTo("text/plain");
            assertThat(message.severity()).isNull();
            assertThat(message.presentation()).isEqualTo("inline");
            assertThat(message.imageUrl()).isNull();
            assertThat(message.url()).isNull();
            assertThat(message.content())
                    .contains("merchant.example")
                    .doesNotContain("profile-source.transport.test")
                    .doesNotContain("advertised.transport.test")
                    .doesNotContain("profile-mcp.transport.test")
                    .doesNotContain("fetched-profile.transport.test")
                    .doesNotContain("integration.transport.test")
                    .doesNotContain("runtime.transport.test");
        });
        assertThat(response.messages().getLast()).satisfies(message -> {
            assertThat(message.type()).isEqualTo("info");
            assertThat(message.content()).isEqualTo("Read the care guide");
            assertThat(message.imageUrl()).isEqualTo("https://cdn.example/care.png");
            assertThat(message.url()).isEqualTo("https://merchant.example/products/shoe");
        });
    }

    @Test
    void sanitizesEveryProviderAuthoredProductDetailFieldAndPreservesBuyerSafeUrls() throws Exception {
        ProductDetailsResponse.SelectedVariant selected = new ProductDetailsResponse.SelectedVariant(
                "selected-1",
                "Selected " + TECHNICAL_ENDPOINT,
                "109.00",
                "USD",
                "SKU " + TECHNICAL_ENDPOINT,
                null,
                TECHNICAL_ENDPOINT + "/selected.png",
                "Selected image " + TECHNICAL_ENDPOINT,
                List.of(),
                true,
                List.of(new ProductDetailsResponse.SelectedOption(
                        "Size " + TECHNICAL_ENDPOINT,
                        "Large " + TECHNICAL_ENDPOINT))
        );
        ProductDetailsResponse.Variant variant = new ProductDetailsResponse.Variant(
                "variant-1",
                "large",
                "Variant " + TECHNICAL_ENDPOINT,
                "Variant description " + TECHNICAL_ENDPOINT,
                TECHNICAL_ENDPOINT + "/products/private",
                "109.00",
                "USD",
                "VARIANT " + TECHNICAL_ENDPOINT,
                null,
                "https://cdn.merchant.example/variant.png",
                "Variant image " + TECHNICAL_ENDPOINT,
                List.of(new ProductDetailsResponse.Media(
                        "image",
                        TECHNICAL_ENDPOINT + "/variant-media.png",
                        "Variant media " + TECHNICAL_ENDPOINT,
                        "https://cdn.merchant.example/variant-preview.png")),
                true,
                List.of(new ProductDetailsResponse.SelectedOption(
                        "Color " + TECHNICAL_ENDPOINT,
                        "Blue " + TECHNICAL_ENDPOINT)),
                List.of(new ProductDetailsResponse.Category(
                        "Shoes " + TECHNICAL_ENDPOINT,
                        "Apparel " + TECHNICAL_ENDPOINT)),
                List.of("Running " + TECHNICAL_ENDPOINT),
                new ObjectMapper().readTree("""
                        {"material": "Mesh %s"}
                        """.formatted(TECHNICAL_ENDPOINT))
        );
        ProductDetailsResponse.Product product = new ProductDetailsResponse.Product(
                "product-1",
                "shoe",
                "Product " + TECHNICAL_ENDPOINT,
                "Description " + TECHNICAL_ENDPOINT,
                "https://merchant.example/products/shoe",
                TECHNICAL_ENDPOINT + "/primary.png",
                List.of(
                        new ProductDetailsResponse.Image(
                                TECHNICAL_ENDPOINT + "/private.png",
                                "Private image " + TECHNICAL_ENDPOINT),
                        new ProductDetailsResponse.Image(
                                "https://cdn.merchant.example/product.png",
                                "Product image " + TECHNICAL_ENDPOINT)),
                List.of(new ProductDetailsResponse.Media(
                        TECHNICAL_ENDPOINT,
                        TECHNICAL_ENDPOINT + "/media.png",
                        "Media " + TECHNICAL_ENDPOINT,
                        "https://cdn.merchant.example/preview.png")),
                List.of(new ProductDetailsResponse.Category(
                        "Footwear " + TECHNICAL_ENDPOINT,
                        "Clothing " + TECHNICAL_ENDPOINT)),
                List.of("Sport " + TECHNICAL_ENDPOINT),
                List.of(new ProductDetailsResponse.Option(
                        "Size " + TECHNICAL_ENDPOINT,
                        List.of("Large " + TECHNICAL_ENDPOINT))),
                List.of(variant),
                1,
                new ProductDetailsResponse.PriceRange("109.00", "109.00", "USD"),
                null,
                null,
                null,
                null,
                true,
                List.of(),
                List.of("SKU " + TECHNICAL_ENDPOINT),
                List.of("Certified " + TECHNICAL_ENDPOINT),
                List.of("Mesh " + TECHNICAL_ENDPOINT),
                List.of("Summer " + TECHNICAL_ENDPOINT),
                new ObjectMapper().readTree("""
                        {"support": "%s", "safe": "cotton"}
                        """.formatted(TECHNICAL_ENDPOINT)),
                null,
                List.of("Breathable " + TECHNICAL_ENDPOINT),
                selected
        );
        ProductDetailsResult result = new ProductDetailsResult(
                TECHNICAL_ENDPOINT,
                "redacted",
                product,
                List.of(),
                NegotiatedCapabilities.none(),
                MERCHANT_ORIGIN,
                List.of("https://profile-transport.internal.test/ucp-profile.json")
        );

        MerchantProductDetailsResponse response = MerchantProductDetailsResponse.from(result);

        assertThat(response.title()).isEqualTo("Product merchant.example");
        assertThat(response.description()).isEqualTo("Description merchant.example");
        assertThat(response.url()).isEqualTo("https://merchant.example/products/shoe");
        assertThat(response.imageUrl()).isNull();
        assertThat(response.images().getFirst()).satisfies(image -> {
            assertThat(image.url()).isNull();
            assertThat(image.altText()).isEqualTo("Private image merchant.example");
        });
        assertThat(response.images().getLast().url())
                .isEqualTo("https://cdn.merchant.example/product.png");
        assertThat(response.media().getFirst()).satisfies(media -> {
            assertThat(media.type()).isEqualTo("other");
            assertThat(media.url()).isNull();
            assertThat(media.altText()).isEqualTo("Media merchant.example");
            assertThat(media.previewImageUrl())
                    .isEqualTo("https://cdn.merchant.example/preview.png");
        });
        assertThat(response.categories().getFirst().value()).isEqualTo("Footwear merchant.example");
        assertThat(response.tags()).containsExactly("Sport merchant.example");
        assertThat(response.options().getFirst()).satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size merchant.example");
            assertThat(option.values()).containsExactly("Large merchant.example");
        });
        assertThat(response.variants().getFirst()).satisfies(sanitizedVariant -> {
            assertThat(sanitizedVariant.title()).isEqualTo("Variant merchant.example");
            assertThat(sanitizedVariant.description())
                    .isEqualTo("Variant description merchant.example");
            assertThat(sanitizedVariant.url()).isNull();
            assertThat(sanitizedVariant.imageUrl())
                    .isEqualTo("https://cdn.merchant.example/variant.png");
            assertThat(sanitizedVariant.imageAltText())
                    .isEqualTo("Variant image merchant.example");
            assertThat(sanitizedVariant.media().getFirst().type()).isEqualTo("image");
            assertThat(sanitizedVariant.attributes().getFirst().value())
                    .isEqualTo("Mesh merchant.example");
        });
        assertThat(response.selectedVariantTitle()).isEqualTo("Selected merchant.example");
        assertThat(response.selectedVariantImageUrl()).isNull();
        assertThat(response.selectedVariantImageAltText())
                .isEqualTo("Selected image merchant.example");
        assertThat(response.selectedOptions().getFirst().value())
                .isEqualTo("Large merchant.example");
        assertThat(response.attributes())
                .extracting(MerchantProductDetailsResponse.ProductAttributeResponse::value)
                .contains("merchant.example", "cotton", "Breathable merchant.example");
        assertThat(response.toString()).doesNotContain("catalog-transport.internal.test");
    }
}

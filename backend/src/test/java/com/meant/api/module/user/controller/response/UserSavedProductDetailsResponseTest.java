package com.meant.api.module.user.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserSavedProductDetailsResponseTest {

    private static final String MERCHANT_ORIGIN = "merchant.example";
    private static final String TECHNICAL_ENDPOINT =
            "https://saved-detail-transport.internal.test/catalog-mcp";

    @Test
    void sanitizesTransientProviderDetailsUsingTheirVerifiedBuyerContext() {
        RehydratedProductDetails.Variant selected = new RehydratedProductDetails.Variant(
                "variant-1",
                "large",
                "Variant " + TECHNICAL_ENDPOINT,
                "Variant description " + TECHNICAL_ENDPOINT,
                TECHNICAL_ENDPOINT + "/products/private",
                "109.00",
                "USD",
                null,
                null,
                "SKU " + TECHNICAL_ENDPOINT,
                "https://cdn.merchant.example/variant.png",
                "Variant image " + TECHNICAL_ENDPOINT,
                List.of(new RehydratedProductDetails.Media(
                        "image",
                        TECHNICAL_ENDPOINT + "/media.png",
                        "Media " + TECHNICAL_ENDPOINT,
                        "https://cdn.merchant.example/preview.png")),
                true,
                List.of(new RehydratedProductDetails.SelectedOption(
                        "Size " + TECHNICAL_ENDPOINT,
                        "Large " + TECHNICAL_ENDPOINT)),
                List.of(new RehydratedProductDetails.Category(
                        "Shoes " + TECHNICAL_ENDPOINT,
                        "Apparel " + TECHNICAL_ENDPOINT)),
                List.of("Running " + TECHNICAL_ENDPOINT),
                List.of(new RehydratedProductDetails.Attribute(
                        "Material " + TECHNICAL_ENDPOINT,
                        "Mesh " + TECHNICAL_ENDPOINT))
        );
        RehydratedProductDetails details = new RehydratedProductDetails(
                "product-1",
                "shoe",
                "Product " + TECHNICAL_ENDPOINT,
                "Description " + TECHNICAL_ENDPOINT,
                "https://merchant.example/products/shoe",
                TECHNICAL_ENDPOINT + "/primary.png",
                List.of(
                        new RehydratedProductDetails.Image(
                                TECHNICAL_ENDPOINT + "/private.png",
                                "Private image " + TECHNICAL_ENDPOINT),
                        new RehydratedProductDetails.Image(
                                "https://cdn.merchant.example/product.png",
                                "Product image " + TECHNICAL_ENDPOINT)),
                List.of(new RehydratedProductDetails.Media(
                        TECHNICAL_ENDPOINT,
                        TECHNICAL_ENDPOINT + "/product-media.png",
                        "Product media " + TECHNICAL_ENDPOINT,
                        "https://cdn.merchant.example/product-preview.png")),
                List.of(new RehydratedProductDetails.Category(
                        "Footwear " + TECHNICAL_ENDPOINT,
                        "Clothing " + TECHNICAL_ENDPOINT)),
                List.of("Sport " + TECHNICAL_ENDPOINT),
                List.of(new RehydratedProductDetails.Option(
                        "Size " + TECHNICAL_ENDPOINT,
                        List.of("Large " + TECHNICAL_ENDPOINT),
                        List.of(new RehydratedProductDetails.OptionValue(
                                "Large " + TECHNICAL_ENDPOINT,
                                true,
                                true)))),
                List.of(new RehydratedProductDetails.SelectedOption(
                        "Size " + TECHNICAL_ENDPOINT,
                        "Large " + TECHNICAL_ENDPOINT)),
                List.of(selected),
                1,
                new RehydratedProductDetails.PriceRange("109.00", "109.00", "USD"),
                null,
                false,
                selected,
                List.of("SKU " + TECHNICAL_ENDPOINT),
                List.of("Certified " + TECHNICAL_ENDPOINT),
                List.of("Mesh " + TECHNICAL_ENDPOINT),
                List.of("Summer " + TECHNICAL_ENDPOINT),
                List.of(new RehydratedProductDetails.Attribute(
                        "Support " + TECHNICAL_ENDPOINT,
                        TECHNICAL_ENDPOINT)),
                List.of(new RehydratedProductDetails.Message(
                        "info",
                        "DETAIL_NOTE",
                        "/product",
                        "text/plain",
                        "Message " + TECHNICAL_ENDPOINT,
                        "info",
                        "inline",
                        TECHNICAL_ENDPOINT + "/message.png",
                        "https://merchant.example/products/shoe")),
                4.8d,
                5.0d,
                42L,
                "Merchant " + TECHNICAL_ENDPOINT,
                MERCHANT_ORIGIN,
                List.of(TECHNICAL_ENDPOINT)
        );

        UserSavedProductDetailsResponse response = UserSavedProductDetailsResponse.from(details);

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
            assertThat(media.altText()).isEqualTo("Product media merchant.example");
            assertThat(media.previewImageUrl())
                    .isEqualTo("https://cdn.merchant.example/product-preview.png");
        });
        assertThat(response.categories().getFirst().value()).isEqualTo("Footwear merchant.example");
        assertThat(response.tags()).containsExactly("Sport merchant.example");
        assertThat(response.options().getFirst()).satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size merchant.example");
            assertThat(option.values()).containsExactly("Large merchant.example");
            assertThat(option.valueDetails().getFirst().value())
                    .isEqualTo("Large merchant.example");
        });
        assertThat(response.variants().getFirst()).satisfies(variant -> {
            assertThat(variant.title()).isEqualTo("Variant merchant.example");
            assertThat(variant.description()).isEqualTo("Variant description merchant.example");
            assertThat(variant.url()).isNull();
            assertThat(variant.imageUrl()).isEqualTo("https://cdn.merchant.example/variant.png");
            assertThat(variant.imageAltText()).isEqualTo("Variant image merchant.example");
            assertThat(variant.media().getFirst().type()).isEqualTo("image");
            assertThat(variant.attributes().getFirst().value()).isEqualTo("Mesh merchant.example");
        });
        assertThat(response.selectedVariantTitle()).isEqualTo("Variant merchant.example");
        assertThat(response.selectedVariantImageUrl())
                .isEqualTo("https://cdn.merchant.example/variant.png");
        assertThat(response.selectedVariantImageAltText())
                .isEqualTo("Variant image merchant.example");
        assertThat(response.selectedOptions().getFirst().value())
                .isEqualTo("Large merchant.example");
        assertThat(response.attributes().getFirst().value()).isEqualTo("merchant.example");
        assertThat(response.messages().getFirst()).satisfies(message -> {
            assertThat(message.content()).isEqualTo("Message merchant.example");
            assertThat(message.imageUrl()).isNull();
            assertThat(message.url()).isEqualTo("https://merchant.example/products/shoe");
        });
        assertThat(response.merchantName()).isEqualTo("Merchant merchant.example");
        assertThat(response.toString()).doesNotContain("saved-detail-transport.internal.test");
    }
}

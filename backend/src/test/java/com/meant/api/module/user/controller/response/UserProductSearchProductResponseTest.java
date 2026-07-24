package com.meant.api.module.user.controller.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserProductSearchProductResponseTest {

    private static final String MERCHANT_DOMAIN = "merchant.example";
    private static final String ENDPOINT = "https://routing.vendor.test/private-catalog-rpc";
    private static final String SAFE_PRODUCT_URL = "https://merchant.example/products/shoe";
    private static final String SAFE_IMAGE_URL = "https://cdn.merchant.example/shoe.jpg";

    @Test
    void sanitizesProviderTextAndRejectsTransportCoordinatesWhileKeepingStorefrontUrls() throws Exception {
        UserProductSearchProductResponse response = UserProductSearchProductResponse.from(result());

        assertThat(response.merchantName()).isEqualTo("Store at merchant.example");
        assertThat(response.title()).isEqualTo("Shoe from merchant.example");
        assertThat(response.descriptionHtml()).isEqualTo("<p>Buy from merchant.example</p>");
        assertThat(response.url()).isEqualTo(SAFE_PRODUCT_URL);
        assertThat(response.imageUrl()).isNull();
        assertThat(response.detailImageUrl()).isEqualTo(SAFE_IMAGE_URL);
        assertThat(response.media()).hasSize(2);
        assertThat(response.media().getFirst().url()).isEqualTo(SAFE_IMAGE_URL);
        assertThat(response.media().getLast().url()).isNull();
        assertThat(response.categories().getFirst().value()).isEqualTo("Shoes merchant.example");
        assertThat(response.attributes().getFirst().value()).isEqualTo("Neutral merchant.example");
        assertThat(response.selectedVariantTitle()).isEqualTo("Orange merchant.example");
        assertThat(response.selectedVariantImageUrl()).isNull();
        assertThat(response.whyMeantForYou()).isEqualTo("Recommended by merchant.example");
        assertThat(response.inventoryItemName()).isEqualTo("Existing shoe merchant.example");

        assertThat(new ObjectMapper().writeValueAsString(response))
                .contains(MERCHANT_DOMAIN, SAFE_PRODUCT_URL, SAFE_IMAGE_URL)
                .doesNotContain("routing.vendor.test", ENDPOINT);
    }

    private UserProductSearchProductResult result() {
        return new UserProductSearchProductResult(
                "merchant.example:product-1",
                "hash-1",
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                MERCHANT_DOMAIN,
                tainted("Store at"),
                ENDPOINT,
                1,
                0.9d,
                0.8d,
                "product-1",
                tainted("Shoe from"),
                "<p>" + tainted("Buy from") + "</p>",
                SAFE_PRODUCT_URL,
                ENDPOINT + "/image.jpg",
                8_000L,
                8_000L,
                "USD",
                10_000L,
                "USD",
                4.8d,
                21,
                List.of(
                        new ProductCatalogMedia("image", SAFE_IMAGE_URL, tainted("Front")),
                        new ProductCatalogMedia("video", ENDPOINT + "/video.mp4", tainted("Video"))
                ),
                List.of(new ProductCatalogCategory(tainted("Shoes"), tainted("Apparel"))),
                List.of(tainted("Certified")),
                List.of(tainted("Mesh")),
                List.of(tainted("SKU-1")),
                List.of(tainted("Running")),
                List.of(new ProductCatalogAttribute(tainted("Support"), tainted("Neutral"))),
                true,
                tainted("Detail warning"),
                tainted("Detailed description"),
                SAFE_IMAGE_URL,
                "80.00",
                "80.00",
                "USD",
                "variant-1",
                tainted("Orange"),
                "80.00",
                "USD",
                ENDPOINT + "/variant.jpg",
                tainted("Orange shoe"),
                true,
                1,
                0.95d,
                1,
                96,
                tainted("Recommended by"),
                List.of("filter-1"),
                List.of(),
                UserInventoryRecommendationRelationship.COMPLEMENT,
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                tainted("Existing shoe")
        );
    }

    private String tainted(String prefix) {
        return prefix + " " + ENDPOINT;
    }
}

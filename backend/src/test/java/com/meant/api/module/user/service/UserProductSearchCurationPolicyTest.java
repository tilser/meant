package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.merchant.service.dto.ProductCatalogMedia;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductSearchCurationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-20T10:00:00Z");

    private final UserProductSearchCurationPolicy service = new UserProductSearchCurationPolicy();

    @Test
    void menFitHidesAmbiguousGenderedApparel() {
        UserProductSearchProductResult menShirt = product(
                "merchant.example:mens-shirt",
                "Men's Camp Shirt - Ensign Blue",
                List.of(category("Clothing")),
                89
        );
        UserProductSearchProductResult ambiguousTank = product(
                "merchant.example:tank",
                "Petals & Patriotism Cotton Blue Avery Mae Graphic Tank Top (Bella+Canvas)",
                List.of(category("Clothing")),
                87
        );
        UserProductSearchProductResult unisexTank = product(
                "merchant.example:unisex-tank",
                "Unisex Blue Tank Top",
                List.of(category("Clothing")),
                86
        );
        UserProductSearchProductResult mug = product(
                "merchant.example:mug",
                "Blue ceramic mug",
                List.of(category("Home")),
                85
        );

        List<UserProductSearchProductResult> visible = service.visibleProducts(
                List.of(menShirt, ambiguousTank, unisexTank, mug),
                settings("men")
        );

        assertThat(visible)
                .extracting(UserProductSearchProductResult::productKey)
                .containsExactly(
                        "merchant.example:mens-shirt",
                        "merchant.example:unisex-tank",
                        "merchant.example:mug"
                );
    }

    @Test
    void womenFitHidesMenApparel() {
        UserProductSearchProductResult womenShirt = product(
                "merchant.example:womens-shirt",
                "Women's Blue Oxford Shirt",
                List.of(category("Clothing")),
                88
        );
        UserProductSearchProductResult menShirt = product(
                "merchant.example:mens-shirt",
                "Men's Blue Oxford Shirt",
                List.of(category("Clothing")),
                87
        );

        List<UserProductSearchProductResult> visible = service.visibleProducts(
                List.of(womenShirt, menShirt),
                settings("women")
        );

        assertThat(visible)
                .extracting(UserProductSearchProductResult::productKey)
                .containsExactly("merchant.example:womens-shirt");
    }

    @Test
    void adultWomenApparelIsNotUnisexForMenFit() {
        UserProductSearchProductResult adultWomenShirt = product(
                "merchant.example:adult-women",
                "Adult Women's Blue Shirt",
                List.of(category("Clothing")),
                88
        );

        List<UserProductSearchProductResult> visible = service.visibleProducts(
                List.of(adultWomenShirt),
                settings("men")
        );

        assertThat(visible).isEmpty();
    }

    @Test
    void fitGateDoesNotRunWithoutClothingFit() {
        UserProductSearchProductResult ambiguousTank = product(
                "merchant.example:tank",
                "Blue Graphic Tank Top",
                List.of(category("Clothing")),
                87
        );

        List<UserProductSearchProductResult> visible = service.visibleProducts(
                List.of(ambiguousTank),
                settings(null)
        );

        assertThat(visible)
                .extracting(UserProductSearchProductResult::productKey)
                .containsExactly("merchant.example:tank");
    }

    @Test
    void keepsExistingScoreThreshold() {
        UserProductSearchProductResult lowScore = product(
                "merchant.example:low-score",
                "Men's Blue Shirt",
                List.of(category("Clothing")),
                49
        );

        List<UserProductSearchProductResult> visible = service.visibleProducts(
                List.of(lowScore),
                settings("men")
        );

        assertThat(visible).isEmpty();
    }

    private UserSettingsResult settings(String clothingFit) {
        return new UserSettingsResult(
                null,
                clothingFit,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                NOW,
                NOW
        );
    }

    private ProductCatalogCategory category(String value) {
        return new ProductCatalogCategory(value, null);
    }

    private UserProductSearchProductResult product(
            String productKey,
            String title,
            List<ProductCatalogCategory> categories,
            int score
    ) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                null,
                1,
                0.9d,
                0.8d,
                productKey.substring(productKey.indexOf(':') + 1),
                title,
                null,
                "https://merchant.example/products/" + productKey,
                "https://merchant.example/image.jpg",
                3800L,
                3800L,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                categories,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProductCatalogAttribute("audience", title)),
                true,
                null,
                title,
                null,
                List.of(),
                List.of(),
                "38.00",
                "38.00",
                "USD",
                1,
                false,
                List.of(),
                "variant-1",
                "Default",
                List.of(),
                "38.00",
                "USD",
                "https://merchant.example/image.jpg",
                title,
                true,
                1,
                0.92d,
                1
        );
        return UserProductSearchProductResult.from(
                UserProductSearchResultItem.from(
                        UUID.randomUUID(),
                        productKey,
                        "hash-" + productKey,
                        product,
                        NOW
                ),
                new UserProductRecommendationExplanationResult(
                        productKey,
                        "hash-" + productKey,
                        "Matches your profile.",
                        List.of(),
                        List.of(),
                        UserInventoryRecommendationRelationship.NONE,
                        null,
                        null
                ),
                new UserProductSearchProductResult.RichCatalogData(
                        List.of(new ProductCatalogMedia("image", "https://merchant.example/image.jpg", title)),
                        categories,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new ProductCatalogAttribute("audience", title))
                )
        ).withMatchScore(score);
    }
}

package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.merchant.service.dto.ProductCatalogAttribute;
import com.meant.api.module.merchant.service.dto.ProductCatalogCategory;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductPreferenceMatchCuratorServiceTest {

    private final UserProductPreferenceMatchCuratorService service = new UserProductPreferenceMatchCuratorService();

    @Test
    void keepsOnlyPreferenceMatchesBackedByProductEvidence() {
        UserProductSearchProductSnapshot product = snapshot(
                "organic-tee",
                "Organic Cotton Tee",
                "Made from 100% organic cotton.",
                List.of("Organic cotton"),
                4.6d,
                128
        );
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                product.productKey(),
                product.productHash(),
                "Organic cotton, strong reviews, and crypto all match.",
                List.of("organic-cotton", "crypto"),
                List.of("no-polyester"),
                UserInventoryRecommendationRelationship.NONE,
                null,
                null
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.curate(
                List.of(product),
                settings(),
                Map.of(product.productKey(), explanation),
                Map.of()
        );

        assertThat(result.get(product.productKey()).matchedFilterIds())
                .containsExactly("organic-cotton", "highly-rated");
        assertThat(result.get(product.productKey()).missedFilterIds()).isEmpty();
        assertThat(result.get(product.productKey()).whyMeantForYou())
                .contains("Organic cotton", "Strong reviews")
                .doesNotContain("Crypto");
    }

    @Test
    void keepsAvoidFilterTradeOffOnlyWhenCatalogShowsAvoidedTerm() {
        UserProductSearchProductSnapshot product = snapshot(
                "polyester-tee",
                "Training Tee",
                "100% polyester jersey.",
                List.of("Polyester"),
                null,
                0
        );
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                product.productKey(),
                product.productHash(),
                "May miss no polyester.",
                List.of(),
                List.of("no-polyester"),
                UserInventoryRecommendationRelationship.NONE,
                null,
                null
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.curate(
                List.of(product),
                settings(),
                Map.of(product.productKey(), explanation),
                Map.of()
        );

        assertThat(result.get(product.productKey()).matchedFilterIds()).isEmpty();
        assertThat(result.get(product.productKey()).missedFilterIds()).containsExactly("no-polyester");
        assertThat(result.get(product.productKey()).whyMeantForYou()).contains("No polyester");
    }

    @Test
    void matchesPreferenceEvidenceNextToPunctuation() {
        UserProductSearchProductSnapshot product = snapshot(
                "gots-socks",
                "Crew Socks",
                "Certified GOTS.",
                List.of(),
                null,
                0
        );
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                product.productKey(),
                product.productHash(),
                "Organic certification.",
                List.of(),
                List.of(),
                UserInventoryRecommendationRelationship.NONE,
                null,
                null
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.curate(
                List.of(product),
                settingsWithOrganic(),
                Map.of(product.productKey(), explanation),
                Map.of()
        );

        assertThat(result.get(product.productKey()).matchedFilterIds()).containsExactly("organic");
        assertThat(result.get(product.productKey()).whyMeantForYou()).contains("Organic");
    }

    @Test
    void preservesNonAsciiPreferenceTokens() {
        UserProductSearchProductSnapshot notTokyo = snapshot(
                "kyoto-cotton",
                "Organic Cotton Tee",
                "Made in Kyoto from organic cotton.",
                List.of("cotton"),
                null,
                0
        );
        UserProductSearchProductSnapshot tokyo = snapshot(
                "tokyo-cotton",
                "Organic Cotton Tee",
                "Made in 東京 from organic cotton.",
                List.of("cotton"),
                null,
                0
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.curate(
                List.of(notTokyo, tokyo),
                settingsWithTokyoCotton(),
                Map.of(),
                Map.of()
        );

        assertThat(result.get(notTokyo.productKey()).matchedFilterIds()).isEmpty();
        assertThat(result.get(tokyo.productKey()).matchedFilterIds()).containsExactly("tokyo-cotton");
    }

    @Test
    void ignoresNullFilterLabelsAndCatalogElements() {
        UserProductSearchProductSnapshot product = snapshotWithNullableCatalog();
        UserProductRecommendationExplanationResult explanation = new UserProductRecommendationExplanationResult(
                product.productKey(),
                product.productHash(),
                "Organic cotton match.",
                List.of("organic-cotton"),
                List.of(),
                UserInventoryRecommendationRelationship.NONE,
                null,
                null
        );

        Map<String, UserProductRecommendationExplanationResult> result = service.curate(
                List.of(product),
                settingsWithNullableLabel(),
                Map.of(product.productKey(), explanation),
                Map.of()
        );

        assertThat(result.get(product.productKey()).matchedFilterIds()).containsExactly("organic-cotton");
        assertThat(result.get(product.productKey()).whyMeantForYou()).doesNotContain("null");
    }

    private UserSettingsResult settings() {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                List.of(
                        filter("organic-cotton", "Organic cotton", "Prefer certified organic cotton.", "materials", "prefer", 10),
                        filter("no-polyester", "No polyester", "Avoid polyester.", "materials", "avoid", 20),
                        filter("crypto", "Crypto", "Prefer tasteful crypto references.", "interests", "prefer", 30),
                        filter("highly-rated", "Strong reviews", "Prefer products with strong ratings.", "shopping", "prefer", 40)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private ShoppingFilterResult filter(
            String id,
            String label,
            String description,
            String category,
            String polarity,
            int displayOrder
    ) {
        return new ShoppingFilterResult(id, label, description, category, polarity, displayOrder);
    }

    private UserSettingsResult settingsWithOrganic() {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                List.of(filter("organic", "Organic", "Prefer organic certifications.", "materials", "prefer", 10)),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private UserSettingsResult settingsWithTokyoCotton() {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                List.of(filter("tokyo-cotton", "東京 cotton", "Prefer cotton from 東京.", "materials", "prefer", 10)),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private UserSettingsResult settingsWithNullableLabel() {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                Arrays.asList(
                        filter(null, "Ignored filter", "Missing filter id.", "materials", "prefer", 5),
                        filter("organic-cotton", null, "Prefer certified organic cotton.", "materials", "prefer", 10)
                ),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private UserProductSearchProductSnapshot snapshot(
            String productId,
            String title,
            String detailDescription,
            List<String> materials,
            Double ratingScore,
            Integer reviewCount
    ) {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                null,
                1,
                0.9d,
                0.8d,
                productId,
                title,
                null,
                "https://merchant.example/" + productId,
                null,
                3800L,
                3800L,
                "USD",
                null,
                null,
                ratingScore,
                reviewCount,
                List.of(),
                List.of(),
                List.of(),
                materials,
                List.of(),
                List.of(),
                List.of(new ProductCatalogAttribute("fabric", detailDescription)),
                true,
                null,
                detailDescription,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                true,
                1,
                0.8d,
                1
        );
        return new UserProductSearchProductSnapshot(
                "merchant.example:" + productId,
                "hash-" + productId,
                product
        );
    }

    private UserProductSearchProductSnapshot snapshotWithNullableCatalog() {
        MerchantSemanticProductResult product = new MerchantSemanticProductResult(
                UUID.randomUUID(),
                "merchant.example",
                "Merchant",
                null,
                1,
                0.9d,
                0.8d,
                "nullable-catalog",
                "Organic Cotton Socks",
                null,
                "https://merchant.example/nullable-catalog",
                null,
                1800L,
                1800L,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                Arrays.asList(null, new ProductCatalogCategory("Socks", null)),
                List.of(),
                List.of("Organic cotton"),
                List.of(),
                List.of(),
                Arrays.asList(null, new ProductCatalogAttribute("fabric", "Organic cotton")),
                true,
                null,
                "Made with organic cotton.",
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                true,
                1,
                0.8d,
                1
        );
        return new UserProductSearchProductSnapshot(
                "merchant.example:nullable-catalog",
                "hash-nullable-catalog",
                product
        );
    }
}

package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import com.meant.api.module.user.constant.UserInventoryRecommendationRelationship;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.entity.UserProductSearchResultItem;
import com.meant.api.module.user.service.dto.UserProductRecommendationExplanationResult;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTasteRankingServiceTest {

    private final UserTasteRankingService service = new UserTasteRankingService();

    @Test
    void learnedFilterSignalsAdjustSubsequentRanking() {
        UserProductSearchProductResult linen = product(
                "merchant.example:linen",
                "Linen shirt",
                List.of("linen"),
                List.of(),
                72,
                2
        );
        UserProductSearchProductResult polyester = product(
                "merchant.example:polyester",
                "Polyester shirt",
                List.of(),
                List.of("linen"),
                74,
                1
        );

        List<UserProductSearchProductResult> ranked = service.rank(
                List.of(polyester, linen),
                tasteProfile(signal("linen", 3.0d)),
                settings(List.of())
        );

        assertThat(ranked).extracting(UserProductSearchProductResult::productKey)
                .containsExactly("merchant.example:linen", "merchant.example:polyester");
        assertThat(ranked.getFirst().matchScore()).isGreaterThan(linen.matchScore());
        assertThat(ranked.getLast().matchScore()).isLessThan(polyester.matchScore());
    }

    @Test
    void explicitFiltersTakePrecedenceOverLearnedSignals() {
        UserProductSearchProductResult linen = product(
                "merchant.example:linen",
                "Linen shirt",
                List.of("linen"),
                List.of(),
                72,
                1
        );

        List<UserProductSearchProductResult> ranked = service.rank(
                List.of(linen),
                tasteProfile(signal("linen", 3.0d)),
                settings(List.of(new ShoppingFilterResult(
                        "linen",
                        "Linen",
                        "Prefer linen.",
                        "materials",
                        "prefer",
                        330
                )))
        );

        assertThat(ranked).singleElement()
                .satisfies(product -> assertThat(product.matchScore()).isEqualTo(linen.matchScore()));
    }

    private UserTasteProfileResult tasteProfile(UserTasteSignalResult signal) {
        return new UserTasteProfileResult("profile", List.of(signal), List.of());
    }

    private UserTasteSignalResult signal(String filterId, double weight) {
        return new UserTasteSignalResult(
                UUID.randomUUID(),
                UserTasteSignalType.FILTER,
                filterId,
                "Linen",
                weight,
                3,
                0,
                "SAVE",
                filterId,
                UserTasteSuggestionStatus.PENDING,
                UserTasteSignalStatus.ACTIVE,
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private UserSettingsResult settings(List<ShoppingFilterResult> filters) {
        return new UserSettingsResult(
                null,
                null,
                null,
                List.of(),
                filters,
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-20T10:00:00Z"),
                Instant.parse("2026-06-20T10:00:00Z")
        );
    }

    private UserProductSearchProductResult product(
            String productKey,
            String title,
            List<String> matchedFilterIds,
            List<String> missedFilterIds,
            int matchScore,
            int rank
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
                null,
                null,
                null,
                null,
                "USD",
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                null,
                title,
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
                rank,
                0.8d,
                rank
        );
        return UserProductSearchProductResult.from(
                UserProductSearchResultItem.from(
                        UUID.randomUUID(),
                        productKey,
                        "hash-" + productKey,
                        product,
                        Instant.parse("2026-06-20T10:00:00Z")
                ),
                new UserProductRecommendationExplanationResult(
                        productKey,
                        "hash-" + productKey,
                        "Matches your profile.",
                        matchedFilterIds,
                        missedFilterIds,
                        UserInventoryRecommendationRelationship.NONE,
                        null,
                        null
                )
        ).withMatchScore(matchScore);
    }
}

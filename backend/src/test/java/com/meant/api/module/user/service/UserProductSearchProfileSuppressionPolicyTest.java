package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductSearchProfileSuppressionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private final UserProductSearchProfileSuppressionPolicy policy =
            new UserProductSearchProfileSuppressionPolicy();

    @Test
    void clearsKnownDimensionSettingsAndKeepsUnrelatedStableFilters() {
        UserLocationResult location = new UserLocationResult(
                "home", "United States", "US", "CA", "94107", "California", "San Francisco");
        UserSettingsResult settings = settings(
                20_000,
                "men",
                location,
                List.of(
                        filter("plus-size-available", "Plus size available"),
                        filter("highly-rated", "Strong reviews"),
                        filter("premium-quality", "Premium quality"),
                        filter("secondhand-or-refurbished", "Secondhand or refurbished"),
                        filter("made-in-usa", "Made in USA"),
                        filter("organic-cotton", "Organic cotton")
                )
        );

        UserSettingsResult sanitized = policy.settings(settings, Set.of(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.RATING,
                UserProductSearchQuestionTarget.PRICE_TIER,
                UserProductSearchQuestionTarget.CONDITION,
                UserProductSearchQuestionTarget.SHIPS_FROM,
                UserProductSearchQuestionTarget.TARGET_GENDER,
                UserProductSearchQuestionTarget.SHIPS_TO
        ));

        assertThat(sanitized.budget()).isNull();
        assertThat(sanitized.clothingFit()).isNull();
        assertThat(sanitized.location()).isNull();
        assertThat(sanitized.locations()).isEmpty();
        assertThat(sanitized.filters())
                .extracting(ShoppingFilterResult::id)
                .containsExactly("organic-cotton");
    }

    @Test
    void colorSuppressionDoesNotConfuseFoodArtificialColorsWithAProductColor() {
        UserSettingsResult sanitized = policy.settings(
                settings(
                        null,
                        null,
                        null,
                        List.of(filter("avoid-artificial-colors", "Avoid artificial colors"))
                ),
                Set.of(UserProductSearchQuestionTarget.COLOR)
        );

        assertThat(sanitized.filters())
                .extracting(ShoppingFilterResult::id)
                .containsExactly("avoid-artificial-colors");
    }

    @Test
    void anyRequestOverrideDropsUntypedHistoricalQueriesAndTargetedCategoryOrFilterTaste() {
        UserTasteProfileResult taste = new UserTasteProfileResult(
                "persisted-hash",
                List.of(
                        taste(UserTasteSignalType.QUERY, "running shoes 46", "running shoes 46"),
                        taste(UserTasteSignalType.QUERY, "jackets shipped to San Francisco",
                                "jackets shipped to San Francisco"),
                        taste(UserTasteSignalType.CATEGORY, "womens-clothing", "Women's clothing"),
                        taste(UserTasteSignalType.FILTER, "opaque-color", "Black"),
                        taste(UserTasteSignalType.MATERIAL, "linen", "Linen")
                ),
                List.of()
        );

        UserTasteProfileResult sanitized = policy.tasteProfile(taste, Set.of(
                UserProductSearchQuestionTarget.SIZE,
                UserProductSearchQuestionTarget.SHIPS_TO,
                UserProductSearchQuestionTarget.TARGET_GENDER,
                UserProductSearchQuestionTarget.COLOR
        ));

        assertThat(sanitized.signals())
                .extracting(UserTasteSignalResult::signalKey)
                .containsExactly("linen");
        assertThat(sanitized.profileHash())
                .contains("profileSuppression=COLOR,SHIPS_TO,SIZE,TARGET_GENDER");
    }

    @Test
    void suppressesSemanticConditionOriginRatingAndPriceTaste() {
        UserTasteProfileResult taste = new UserTasteProfileResult(
                "persisted-hash",
                List.of(
                        taste(UserTasteSignalType.FILTER, "condition", "Refurbished"),
                        taste(UserTasteSignalType.CATEGORY, "origin", "Locally made"),
                        taste(UserTasteSignalType.CATEGORY, "reviews", "Five star reviews"),
                        taste(UserTasteSignalType.FILTER, "price", "Budget friendly"),
                        taste(UserTasteSignalType.MATERIAL, "wool", "Wool")
                ),
                List.of()
        );

        UserTasteProfileResult sanitized = policy.tasteProfile(taste, Set.of(
                UserProductSearchQuestionTarget.CONDITION,
                UserProductSearchQuestionTarget.SHIPS_FROM,
                UserProductSearchQuestionTarget.RATING,
                UserProductSearchQuestionTarget.PRICE
        ));

        assertThat(sanitized.signals())
                .extracting(UserTasteSignalResult::signalKey)
                .containsExactly("wool");
    }

    private UserSettingsResult settings(
            Integer budget,
            String clothingFit,
            UserLocationResult location,
            List<ShoppingFilterResult> filters
    ) {
        return new UserSettingsResult(
                budget,
                clothingFit,
                location,
                location == null ? List.of() : List.of(location),
                filters,
                List.of(),
                filters.stream().map(ShoppingFilterResult::id).toList(),
                List.of(),
                NOW,
                NOW
        );
    }

    private ShoppingFilterResult filter(String id, String label) {
        return new ShoppingFilterResult(id, label, null, "test", "prefer", 1);
    }

    private UserTasteSignalResult taste(
            UserTasteSignalType type,
            String key,
            String label
    ) {
        return new UserTasteSignalResult(
                UUID.randomUUID(),
                type,
                key,
                label,
                2.0d,
                1,
                0,
                null,
                null,
                UserTasteSuggestionStatus.ACCEPTED,
                UserTasteSignalStatus.ACTIVE,
                NOW,
                NOW
        );
    }
}

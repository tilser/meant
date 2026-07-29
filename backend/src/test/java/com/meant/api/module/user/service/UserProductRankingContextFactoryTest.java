package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import com.meant.api.module.user.constant.UserTasteSignalStatus;
import com.meant.api.module.user.constant.UserTasteSignalType;
import com.meant.api.module.user.constant.UserTasteSuggestionStatus;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserInventoryRecommendationSignal;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import com.meant.api.module.user.service.dto.UserTasteSignalResult;
import com.meant.api.module.catalog.service.dto.ProductRankingContext;
import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProductRankingContextFactoryTest {
    private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

    @Test
    void filterTasteKeepsOpaqueIdentitySeparateFromTrustworthyNormalizedLabel() {
        ProductRankingContext context = factory().create(UUID.randomUUID(), preparation(
                null,
                List.of(taste("opaque-filter-id", "Natural Linen", 2.0d),
                        taste("avoid-filter-id", "No polyester", -2.0d))), List.of());

        assertThat(context.preferences()).containsExactly(
                new ProductRankingContext.PreferenceSignal(
                        ProductRankingContext.PreferenceSignal.Type.FILTER,
                        "opaque-filter-id", "natural linen", 5_000),
                new ProductRankingContext.PreferenceSignal(
                        ProductRankingContext.PreferenceSignal.Type.FILTER,
                        "avoid-filter-id", "polyester", -5_000));
    }

    @Test
    void explicitSettingTakesPrecedenceOverLearnedSignalWithTheSameIdentity() {
        ShoppingFilterResult explicit = new ShoppingFilterResult(
                "same-filter-id", "Organic Cotton", null, "material", "include", 1);
        ProductRankingContext context = factory().create(UUID.randomUUID(), preparation(
                explicit, List.of(taste("same-filter-id", "Polyester", -3.0d))), List.of());

        assertThat(context.preferences()).containsExactly(new ProductRankingContext.PreferenceSignal(
                ProductRankingContext.PreferenceSignal.Type.FILTER,
                "same-filter-id", "organic cotton", 8_000));
    }

    @Test
    void filterTasteWithoutATrustworthyLabelIsIgnoredInsteadOfMatchingItsOpaqueId() {
        ProductRankingContext context = factory().create(UUID.randomUUID(), preparation(
                null, List.of(taste("opaque-filter-id", null, 2.0d))), List.of());

        assertThat(context.preferences()).isEmpty();
    }

    @Test
    void sizeAnyRemovesSizeSettingsAndHistoricalQueryTasteWhileKeepingUnrelatedPreferences() {
        ProductRankingContext context = factory().create(UUID.randomUUID(), preparation(
                List.of(
                        filter("plus-size-available", "Plus size available"),
                        filter("organic-cotton", "Organic cotton")
                ),
                List.of(
                        taste(UserTasteSignalType.QUERY, "running shoes 46", "running shoes 46", 2.0d),
                        taste(UserTasteSignalType.FILTER, "opaque-size", "EU 46", 2.0d),
                        taste(UserTasteSignalType.MATERIAL, "linen", "Linen", 2.0d)
                ),
                Set.of(UserProductSearchQuestionTarget.SIZE),
                Set.of(UserProductSearchQuestionTarget.SIZE)
        ), List.of());

        assertThat(context.preferences())
                .extracting(ProductRankingContext.PreferenceSignal::identity)
                .containsExactly("organic-cotton", "linen");
    }

    @Test
    void buyerGenderValueOverrideRemovesGenderedProfileTasteWithoutBeingAnExplicitAny() {
        ProductRankingContext context = factory().create(UUID.randomUUID(), preparation(
                List.of(filter("organic-cotton", "Organic cotton")),
                List.of(
                        taste(UserTasteSignalType.CATEGORY, "womens-clothing", "Women's clothing", 2.0d),
                        taste(UserTasteSignalType.FILTER, "mens-fit", "Men's fit", 2.0d),
                        taste(UserTasteSignalType.MATERIAL, "merino", "Merino wool", 2.0d)
                ),
                Set.of(),
                Set.of(UserProductSearchQuestionTarget.TARGET_GENDER)
        ), List.of());

        assertThat(context.preferences())
                .extracting(ProductRankingContext.PreferenceSignal::identity)
                .containsExactly("organic-cotton", "merino");
    }

    private UserProductRankingContextFactory factory() {
        return new UserProductRankingContextFactory(new StubInventoryService());
    }

    private UserProductSearchPreparation preparation(
            ShoppingFilterResult explicit,
            List<UserTasteSignalResult> taste
    ) {
        return preparation(
                explicit == null ? List.of() : List.of(explicit),
                taste,
                Set.of(),
                Set.of()
        );
    }

    private UserProductSearchPreparation preparation(
            List<ShoppingFilterResult> filters,
            List<UserTasteSignalResult> taste,
            Set<UserProductSearchQuestionTarget> explicitAnyTargets,
            Set<UserProductSearchQuestionTarget> profileSuppressionTargets
    ) {
        UserSettingsResult settings = filters.isEmpty() ? null : new UserSettingsResult(
                null, null, null, List.of(), filters, List.of(), List.of(), List.of(), NOW, NOW);
        return new UserProductSearchPreparation(
                "shirt", null, settings, new UserTasteProfileResult("hash", taste, List.of()),
                new UserProductSearchCatalogInput("shirt", "shirt", null, null, null),
                "shirt", "hash", NOW, 0, 20, 100,
                explicitAnyTargets, profileSuppressionTargets);
    }

    private ShoppingFilterResult filter(String id, String label) {
        return new ShoppingFilterResult(id, label, null, "test", "include", 1);
    }

    private UserTasteSignalResult taste(String key, String label, double weight) {
        return taste(UserTasteSignalType.FILTER, key, label, weight);
    }

    private UserTasteSignalResult taste(
            UserTasteSignalType type,
            String key,
            String label,
            double weight
    ) {
        return new UserTasteSignalResult(
                UUID.randomUUID(), type, key, label, weight, 1, 0, null, null,
                UserTasteSuggestionStatus.ACCEPTED, UserTasteSignalStatus.ACTIVE, NOW, NOW);
    }

    private static final class StubInventoryService extends UserInventoryService {
        private StubInventoryService() {
            super(null, null, null, null);
        }

        @Override
        public Map<String, UserInventoryRecommendationSignal> canonicalRecommendationSignals(
                UUID userId, List<CanonicalProduct> products
        ) {
            return Map.of();
        }
    }
}

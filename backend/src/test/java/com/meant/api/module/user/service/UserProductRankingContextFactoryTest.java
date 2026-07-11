package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
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

    private UserProductRankingContextFactory factory() {
        return new UserProductRankingContextFactory(new StubInventoryService());
    }

    private UserProductSearchPreparation preparation(
            ShoppingFilterResult explicit,
            List<UserTasteSignalResult> taste
    ) {
        UserSettingsResult settings = explicit == null ? null : new UserSettingsResult(
                null, null, null, List.of(), List.of(explicit), List.of(), List.of(), List.of(), NOW, NOW);
        return new UserProductSearchPreparation(
                "shirt", null, settings, new UserTasteProfileResult("hash", taste, List.of()),
                new UserProductSearchCatalogInput("shirt", "shirt", null, null, null),
                "shirt", "hash", NOW, 0, 20, 100);
    }

    private UserTasteSignalResult taste(String key, String label, double weight) {
        return new UserTasteSignalResult(
                UUID.randomUUID(), UserTasteSignalType.FILTER, key, label, weight, 1, 0, null, null,
                UserTasteSuggestionStatus.ACCEPTED, UserTasteSignalStatus.ACTIVE, NOW, NOW);
    }

    private static final class StubInventoryService extends UserInventoryService {
        private StubInventoryService() {
            super(null, null, null, null, null);
        }

        @Override
        public Map<String, UserInventoryRecommendationSignal> canonicalRecommendationSignals(
                UUID userId, List<CanonicalProduct> products
        ) {
            return Map.of();
        }
    }
}

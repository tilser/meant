package com.meant.api.plugin.catalog.common.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.ProductAttribute;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingResult;
import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductRankingFeatureCorrectnessTest {
    private final ProductRankingService service = ProductRankingTestFactory.service();

    @Test
    void positiveAndNegativeFilterTasteUseNormalizedLabelNotOpaqueIdentity() {
        CanonicalProduct linen = product("linen", "linen shirt", fresh());
        CanonicalProduct cotton = product("cotton", "cotton shirt", fresh());
        CanonicalProduct polyester = product("polyester", "polyester shirt", fresh());
        ProductRankingContext.PreferenceSignal positive = new ProductRankingContext.PreferenceSignal(
                ProductRankingContext.PreferenceSignal.Type.FILTER, "filter:opaque-123", "linen", 8_000);
        ProductRankingContext.PreferenceSignal negative = new ProductRankingContext.PreferenceSignal(
                ProductRankingContext.PreferenceSignal.Type.FILTER, "avoid-filter-id", "polyester", -8_000);

        assertThat(rank(List.of(cotton, linen), List.of(positive)).products())
                .extracting(CanonicalProduct::key).containsExactly("linen", "cotton");
        assertThat(rank(List.of(polyester, cotton), List.of(negative)).products())
                .extracting(CanonicalProduct::key).containsExactly("cotton", "polyester");
    }

    @Test
    void filterAndQueryPreferenceMatchingDoesNotMatchMenInsideWomen() {
        CanonicalProduct men = product("men", "men shirt", fresh());
        CanonicalProduct women = product("women", "women shirt", fresh());
        List.of(ProductRankingContext.PreferenceSignal.Type.FILTER, ProductRankingContext.PreferenceSignal.Type.QUERY)
                .forEach(type -> {
                    ProductRankingContext.PreferenceSignal preference = new ProductRankingContext.PreferenceSignal(
                            type, type.name().toLowerCase() + ":men", "men", 8_000);
                    assertThat(rank(List.of(women, men), List.of(preference)).products())
                            .extracting(CanonicalProduct::key).containsExactly("men", "women");
                });
    }

    @Test
    void explicitExpiryOverridesRecentObservationAndValidMergedProvenanceWins() {
        CanonicalProduct expired = product("expired", "shirt", new ResultFreshness(
                RankingTestFixtures.NOW.minusSeconds(60), RankingTestFixtures.NOW.minusSeconds(1)));
        CanonicalProduct valid = product("valid", "shirt", new ResultFreshness(
                RankingTestFixtures.NOW.minusSeconds(86_400), RankingTestFixtures.NOW.plusSeconds(60)));
        Offer expiredOffer = expired.offers().getFirst();
        Offer validObservation = RankingTestFixtures.offer("MERGED", "merchant-2", "merged", "v2",
                new Money(1_000, "USD"), OfferAvailabilityStatus.IN_STOCK, 5_000, List.of(),
                new ResultFreshness(RankingTestFixtures.NOW.minusSeconds(86_400), RankingTestFixtures.NOW.plusSeconds(60)));
        CanonicalProduct merged = RankingTestFixtures.product(
                "merged", "shirt", "MERGED", "merged-source", 5_000, List.of(), expiredOffer, validObservation);

        ProductRankingResult result = service.rank(List.of(expired, valid, merged), RankingTestFixtures.context("shirt"));

        assertThat(result.products()).extracting(CanonicalProduct::key).containsExactly("merged", "valid", "expired");
        assertThat(feature(result, "expired", ProductRankingExplanation.Name.FRESHNESS).valueBasisPoints()).isZero();
        assertThat(feature(result, "valid", ProductRankingExplanation.Name.FRESHNESS).valueBasisPoints()).isEqualTo(10_000);
        assertThat(feature(result, "merged", ProductRankingExplanation.Name.FRESHNESS).valueBasisPoints()).isEqualTo(10_000);
    }

    @Test
    void valueOnlyCommissionMarkerIsExcludedFromQualityAndIntent() {
        CanonicalProduct plain = product("plain", "coat", fresh());
        CanonicalProduct commercial = withAttributes(product("commercial", "coat", fresh()),
                List.of(new ProductAttribute("ranking", "priority", "affiliate commission sponsored")));

        ProductRankingResult result = service.rank(List.of(commercial, plain), RankingTestFixtures.context("coat"));

        assertThat(result.productExplanations().get("commercial").scoreBasisPoints())
                .isEqualTo(result.productExplanations().get("plain").scoreBasisPoints());
    }

    @Test
    void sparseProductUsesConservativeUnknownPriorAndScoreIsReproducible() {
        CanonicalProduct sparse = product("sparse", null, fresh());
        CanonicalProduct complete = withAttributes(product("complete", "shirt", fresh()),
                List.of(new ProductAttribute("taxonomy", "category", "apparel")));

        ProductRankingResult result = service.rank(List.of(sparse, complete), RankingTestFixtures.context("shirt"));
        ProductRankingExplanation explanation = result.productExplanations().get("sparse");

        assertThat(result.products()).extracting(CanonicalProduct::key).containsExactly("complete", "sparse");
        long weighted = explanation.features().stream().filter(feature -> feature.weight() > 0)
                .mapToLong(feature -> (long) feature.weight() * (feature.valueBasisPoints() == null
                        ? RankingScorePolicy.UNKNOWN_PRIOR_BASIS_POINTS : feature.valueBasisPoints())).sum();
        int weights = explanation.features().stream().mapToInt(ProductRankingExplanation.Feature::weight).sum();
        assertThat(explanation.scoreBasisPoints()).isEqualTo((int) Math.round(weighted / (double) weights));
    }

    private ProductRankingResult rank(List<CanonicalProduct> products, List<ProductRankingContext.PreferenceSignal> preferences) {
        return service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", null, preferences, 20));
    }

    private CanonicalProduct product(String key, String title, ResultFreshness freshness) {
        Offer offer = RankingTestFixtures.offer(key.toUpperCase(), "merchant", key, "v",
                new Money(1_000, "USD"), OfferAvailabilityStatus.IN_STOCK, 5_000, List.of(), freshness);
        return RankingTestFixtures.product(key, title, key.toUpperCase(), key + "-source", 5_000, List.of(), offer);
    }

    private CanonicalProduct withAttributes(CanonicalProduct product, List<ProductAttribute> attributes) {
        return new CanonicalProduct(
                product.key(), product.title(), product.description(), product.media(), attributes, product.materials(),
                product.certifications(), product.attribution(), product.identityEvidence(), product.provenance(),
                product.retrievalSignals(), product.offers());
    }

    private ProductRankingExplanation.Feature feature(
            ProductRankingResult result, String key, ProductRankingExplanation.Name name
    ) {
        return result.productExplanations().get(key).features().stream()
                .filter(feature -> feature.name() == name).findFirst().orElseThrow();
    }

    private ResultFreshness fresh() {
        return new ResultFreshness(
                RankingTestFixtures.NOW.minusSeconds(60), RankingTestFixtures.NOW.plusSeconds(60));
    }
}

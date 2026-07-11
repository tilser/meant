package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ProductDiversityPolicyTest {
    private final ProductRankingService service = ProductRankingTestFactory.service();

    @Test
    void feasibilityAwareSelectionAvoidsCorrelatedSourceMerchantDeadEnd() {
        List<CanonicalProduct> products = List.of(
                product("a1", "A", "X"), product("a2", "A", "X"), product("a3", "A", "X"),
                product("b1", "B", "X"), product("c1", "A", "Y"), product("d1", "B", "Y"));

        ProductRankingResult result = service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", null, List.of(), 5));
        List<CanonicalProduct> window = result.products().stream().limit(5).toList();

        assertThat(window).hasSize(5);
        assertThat(window).filteredOn(product -> product.key().startsWith("a")).hasSize(2);
        assertThat(result.productExplanations().get(window.getFirst().key()).diversityPolicyOutcome())
                .isEqualTo(ProductRankingExplanation.DiversityPolicyOutcome.STRICT);
    }

    @Test
    void capsRelaxOnlyWhenNoStrictFullWindowExistsAndStillReturnTheFullWindow() {
        List<CanonicalProduct> products = List.of(
                product("a1", "A", "X"), product("a2", "A", "X"), product("a3", "A", "X"),
                product("a4", "A", "X"), product("a5", "A", "X"));

        ProductRankingResult result = service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", null, List.of(), 5));

        assertThat(result.products()).hasSize(5);
        assertThat(result.productExplanations().values())
                .extracting(ProductRankingExplanation::diversityPolicyOutcome)
                .containsOnly(ProductRankingExplanation.DiversityPolicyOutcome.RELAXED_INFEASIBLE);
    }

    @Test
    void weakerMultiSourceEvidenceAndUnrelatedOfferDoNotChangeAttributionOrOrder() {
        CanonicalProduct baseline = product("a", "A", "X");
        Offer unrelated = RankingTestFixtures.offer("Z", "UNRELATED", "a", "z",
                new Money(1, "USD"), OfferAvailabilityStatus.IN_STOCK, 10_000, List.of());
        CanonicalProduct augmented = new CanonicalProduct(
                baseline.key(), baseline.title(), baseline.description(), baseline.media(), baseline.attributes(),
                baseline.materials(), baseline.certifications(), baseline.attribution(), baseline.identityEvidence(),
                baseline.provenance(), List.of(
                        baseline.retrievalSignals().getFirst(),
                        RankingTestFixtures.signal("Z", "z-source", unrelated.identity().merchantScope(), 1_000)),
                List.of(baseline.offers().getFirst(), unrelated));
        CanonicalProduct b = product("b", "B", "Y");
        CanonicalProduct c = product("c", "C", "Z");
        List<String> original = keys(service.rank(List.of(baseline, b, c), RankingTestFixtures.context("shirt")));
        List<String> changed = keys(service.rank(List.of(c, augmented, b), RankingTestFixtures.context("shirt")));

        assertThat(changed).containsExactlyElementsOf(original);
    }

    @Test
    void diversityIsPermutationStable() {
        List<CanonicalProduct> products = new ArrayList<>(List.of(
                product("a", "A", "X"), product("b", "A", "Y"), product("c", "B", "X"),
                product("d", "B", "Y"), product("e", "C", "Z")));
        List<String> expected = keys(service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", null, List.of(), 5)));
        Collections.shuffle(products, new Random(42));

        assertThat(keys(service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", null, List.of(), 5)))).containsExactlyElementsOf(expected);
    }

    private CanonicalProduct product(String key, String source, String merchant) {
        Offer offer = RankingTestFixtures.offer(source, merchant, key, "v", new Money(1_000, "USD"),
                OfferAvailabilityStatus.IN_STOCK, 5_000, List.of());
        CanonicalProduct product = RankingTestFixtures.product(key, "shirt", source, source + "-source", 5_000,
                List.of(new ProductAttribute("taxonomy", "category", "apparel")), offer);
        OfferMerchantScope sharedMerchant = OfferMerchantScope.external(new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, "CROSS_PROVIDER_MERCHANT", merchant));
        return RankingTestFixtures.withSignals(product,
                RankingTestFixtures.signal(source, source + "-source", sharedMerchant, 5_000));
    }

    private List<String> keys(ProductRankingResult result) {
        return result.products().stream().map(CanonicalProduct::key).toList();
    }
}

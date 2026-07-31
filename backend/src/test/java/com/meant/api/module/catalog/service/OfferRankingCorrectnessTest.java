package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.DeliveryMethod;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferDelivery;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductRankingResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OfferRankingCorrectnessTest {
    private final ProductRankingService service = ProductRankingTestFactory.service();

    @Test
    void nonPreferredCurrencyOffersAreExcludedAndRankingRemainsPermutationStable() {
        Offer eur = offer("EUR", "eur", new Money(100, "EUR"), OfferAvailabilityStatus.IN_STOCK, 9_000, List.of());
        Offer usd = offer("USD", "usd", new Money(10_000, "USD"), OfferAvailabilityStatus.IN_STOCK, 9_000, List.of());
        List<Offer> reversed = new ArrayList<>(List.of(eur, usd));
        Collections.shuffle(reversed, new Random(42));

        ProductRankingResult first = rank(List.of(eur, usd));
        ProductRankingResult second = rank(reversed);

        assertThat(first.products().getFirst().offers()).extracting(Offer::key)
                .containsExactlyElementsOf(second.products().getFirst().offers().stream().map(Offer::key).toList());
        assertThat(first.products().getFirst().offers()).containsExactly(usd);
        assertThat(first.offerExplanations()).containsKey(usd.key()).doesNotContainKey(eur.key());
    }

    @Test
    void deliveryCostAndSpeedComeFromOneConcreteCheapestChoice() {
        OfferDelivery freeSlow = delivery(0, 10, 10);
        OfferDelivery paidFast = delivery(1_000, 1, 1);
        Offer offer = offer("A", "offer", new Money(5_000, "USD"),
                OfferAvailabilityStatus.IN_STOCK, 9_000, List.of(paidFast, freeSlow));

        OfferRankingExplanation explanation = rank(List.of(offer)).offerExplanations().get(offer.key());

        assertAvailable(explanation, OfferRankingExplanation.Name.LANDED_PRICE, 10_000);
        assertAvailable(explanation, OfferRankingExplanation.Name.DELIVERY_EVIDENCE, 8_000);
    }

    @Test
    void costOnlyAndTimingOnlyDeliveryPreserveUnknownFacts() {
        Offer costOnly = offer("A", "cost", new Money(5_000, "USD"), OfferAvailabilityStatus.IN_STOCK,
                9_000, List.of(delivery(500, null, null)));
        Offer timingOnly = offer("B", "timing", new Money(5_000, "USD"), OfferAvailabilityStatus.IN_STOCK,
                9_000, List.of(new OfferDelivery(DeliveryMethod.SHIPPING, "US", 2, 3, null)));

        ProductRankingResult result = rank(List.of(timingOnly, costOnly));

        assertUnknown(result.offerExplanations().get(costOnly.key()), OfferRankingExplanation.Name.DELIVERY_EVIDENCE);
        assertAvailable(result.offerExplanations().get(costOnly.key()), OfferRankingExplanation.Name.LANDED_PRICE, 10_000);
        assertAvailable(result.offerExplanations().get(timingOnly.key()), OfferRankingExplanation.Name.DELIVERY_EVIDENCE, 9_400);
        assertUnknown(result.offerExplanations().get(timingOnly.key()), OfferRankingExplanation.Name.LANDED_PRICE);
    }

    @Test
    void sparseUnknownOfferCannotBeatConfirmedCompleteOfferAndScoresAreReproducible() {
        Offer sparse = offer("A", "sparse", null, OfferAvailabilityStatus.UNKNOWN, null, List.of());
        Offer confirmed = offer("B", "confirmed", new Money(5_000, "USD"),
                OfferAvailabilityStatus.IN_STOCK, 9_000, List.of(delivery(0, 2, 3)));

        ProductRankingResult result = rank(List.of(sparse, confirmed));
        OfferRankingExplanation sparseExplanation = result.offerExplanations().get(sparse.key());

        assertThat(result.products().getFirst().offers()).extracting(Offer::key)
                .containsExactly(confirmed.key(), sparse.key());
        long weighted = sparseExplanation.features().stream().filter(feature -> feature.weight() > 0)
                .mapToLong(feature -> (long) feature.weight() * (feature.valueBasisPoints() == null
                        ? RankingScorePolicy.UNKNOWN_PRIOR_BASIS_POINTS : feature.valueBasisPoints())).sum();
        int weights = sparseExplanation.features().stream().mapToInt(OfferRankingExplanation.Feature::weight).sum();
        assertThat(sparseExplanation.scoreBasisPoints()).isEqualTo((int) Math.round(weighted / (double) weights));
    }

    private ProductRankingResult rank(List<Offer> offers) {
        CanonicalProduct product = RankingTestFixtures.product(
                "shared", "shirt", "FIXTURE", "source", 5_000,
                List.of(new ProductAttribute("taxonomy", "category", "apparel")), offers.toArray(Offer[]::new));
        return service.rank(List.of(product), RankingTestFixtures.context("shirt"));
    }

    private Offer offer(String provider, String variant, Money price, OfferAvailabilityStatus availability,
                        Integer reliability, List<OfferDelivery> delivery) {
        return RankingTestFixtures.offer(provider, "merchant-" + variant, "shared", variant,
                price, availability, reliability, delivery);
    }

    private OfferDelivery delivery(long cost, Integer minimumDays, Integer maximumDays) {
        return new OfferDelivery(DeliveryMethod.SHIPPING, "US", minimumDays, maximumDays, new Money(cost, "USD"));
    }

    private void assertUnknown(OfferRankingExplanation explanation, OfferRankingExplanation.Name name) {
        assertThat(feature(explanation, name).availability()).isEqualTo(OfferRankingExplanation.Availability.UNKNOWN);
        assertThat(feature(explanation, name).valueBasisPoints()).isNull();
    }

    private void assertAvailable(OfferRankingExplanation explanation, OfferRankingExplanation.Name name, int value) {
        assertThat(feature(explanation, name).valueBasisPoints()).isEqualTo(value);
    }

    private OfferRankingExplanation.Feature feature(OfferRankingExplanation explanation, OfferRankingExplanation.Name name) {
        return explanation.features().stream().filter(feature -> feature.name() == name).findFirst().orElseThrow();
    }
}

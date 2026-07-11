package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.Money;
import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferAvailabilityStatus;
import com.meant.api.plugin.catalog.common.dto.OfferDelivery;
import com.meant.api.plugin.catalog.common.dto.OfferRankingEvidence;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;

/** Ranks exact offers without mutating or substituting their commercial identity. */
@Service
public class OfferRankingService {

    public static final String OFFER_RANKING_VERSION = "offer-v1";

    private static final int LANDED_PRICE_WEIGHT = 25;
    private static final int ITEM_PRICE_WEIGHT = 15;
    private static final int AVAILABILITY_WEIGHT = 30;
    private static final int DELIVERY_WEIGHT = 10;
    private static final int TRUST_WEIGHT = 15;
    private static final int RELIABILITY_WEIGHT = 25;
    private static final int RETURN_POLICY_WEIGHT = 10;
    private static final int CHECKOUT_WEIGHT = 8;
    private static final int FRESHNESS_WEIGHT = 7;
    private static final int COMPLETENESS_WEIGHT = 5;

    public Result rank(List<Offer> offers, Instant rankedAt) {
        List<Offer> safeOffers = offers == null ? List.of() : offers.stream().filter(java.util.Objects::nonNull).toList();
        Map<CurrencyAmount, Long> landedPrices = new HashMap<>();
        Map<CurrencyAmount, Long> itemPrices = new HashMap<>();
        for (Offer offer : safeOffers) {
            if (offer.price() != null) {
                itemPrices.put(new CurrencyAmount(offer.key(), offer.price().currency()), offer.price().minorUnits());
            }
            Long landed = landedPrice(offer);
            if (landed != null) {
                landedPrices.put(new CurrencyAmount(offer.key(), offer.price().currency()), landed);
            }
        }
        Map<String, Range> landedRanges = ranges(landedPrices);
        Map<String, Range> itemRanges = ranges(itemPrices);
        List<ScoredOffer> scored = safeOffers.stream()
                .map(offer -> score(offer, rankedAt, landedPrices, landedRanges, itemPrices, itemRanges))
                .sorted(Comparator.comparingInt(ScoredOffer::scoreBasisPoints)
                        .reversed()
                        .thenComparing(entry -> entry.offer().key()))
                .toList();
        Map<String, OfferRankingExplanation> explanations = new LinkedHashMap<>();
        List<Offer> ranked = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            ScoredOffer entry = scored.get(index);
            ranked.add(entry.offer());
            explanations.put(entry.offer().key(), entry.explanation().withFinalRank(index + 1));
        }
        return new Result(ranked, explanations);
    }

    private ScoredOffer score(
            Offer offer,
            Instant rankedAt,
            Map<CurrencyAmount, Long> landedPrices,
            Map<String, Range> landedRanges,
            Map<CurrencyAmount, Long> itemPrices,
            Map<String, Range> itemRanges
    ) {
        List<OfferRankingExplanation.Feature> features = new ArrayList<>();
        Long landed = offer.price() == null
                ? null
                : landedPrices.get(new CurrencyAmount(offer.key(), offer.price().currency()));
        addPriceFeature(
                features,
                OfferRankingExplanation.Name.LANDED_PRICE,
                landed,
                offer.price() == null ? null : landedRanges.get(offer.price().currency()),
                LANDED_PRICE_WEIGHT
        );
        Long item = offer.price() == null
                ? null
                : itemPrices.get(new CurrencyAmount(offer.key(), offer.price().currency()));
        addPriceFeature(
                features,
                OfferRankingExplanation.Name.ITEM_PRICE,
                item,
                offer.price() == null ? null : itemRanges.get(offer.price().currency()),
                landed == null ? ITEM_PRICE_WEIGHT : 0
        );
        add(features, OfferRankingExplanation.Name.AVAILABILITY, availability(offer), AVAILABILITY_WEIGHT);
        add(features, OfferRankingExplanation.Name.DELIVERY_EVIDENCE, delivery(offer), DELIVERY_WEIGHT);
        OfferRankingEvidence evidence = offer.rankingEvidence();
        add(features, OfferRankingExplanation.Name.MERCHANT_TRUST,
                evidence.merchantTrustBasisPoints(), TRUST_WEIGHT);
        add(features, OfferRankingExplanation.Name.HISTORICAL_RELIABILITY,
                evidence.historicalReliabilityBasisPoints(), RELIABILITY_WEIGHT);
        add(features, OfferRankingExplanation.Name.RETURN_POLICY,
                evidence.returnPolicyBasisPoints(), RETURN_POLICY_WEIGHT);
        add(features, OfferRankingExplanation.Name.CHECKOUT_CAPABILITY,
                checkoutCapability(offer), CHECKOUT_WEIGHT);
        add(features, OfferRankingExplanation.Name.FRESHNESS, freshness(offer, rankedAt), FRESHNESS_WEIGHT);
        add(features, OfferRankingExplanation.Name.DATA_COMPLETENESS,
                completeness(features), COMPLETENESS_WEIGHT);
        int score = weightedScore(features);
        return new ScoredOffer(offer, score, new OfferRankingExplanation(
                OFFER_RANKING_VERSION,
                score,
                1,
                OfferRankingExplanation.CommercialTieBreakPolicy.NONE,
                offer.key(),
                features
        ));
    }

    private void addPriceFeature(
            List<OfferRankingExplanation.Feature> features,
            OfferRankingExplanation.Name name,
            Long amount,
            Range range,
            int weight
    ) {
        add(features, name, amount == null || range == null ? null : range.lowerIsBetter(amount), weight);
    }

    private void add(
            List<OfferRankingExplanation.Feature> features,
            OfferRankingExplanation.Name name,
            Integer value,
            int weight
    ) {
        features.add(value == null
                ? OfferRankingExplanation.Feature.unknown(name, weight)
                : OfferRankingExplanation.Feature.available(name, value, weight));
    }

    private Integer availability(Offer offer) {
        return switch (offer.availability().status()) {
            case IN_STOCK -> 10_000;
            case PREORDER -> 7_500;
            case BACKORDER -> 6_000;
            case OUT_OF_STOCK -> 1_000;
            case DISCONTINUED -> 0;
            case UNKNOWN -> null;
        };
    }

    private Integer delivery(Offer offer) {
        if (offer.delivery().isEmpty()) {
            return null;
        }
        Integer fastestMaximum = offer.delivery().stream()
                .map(OfferDelivery::maximumBusinessDays)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
        return fastestMaximum == null
                ? 6_000
                : Math.max(2_000, 10_000 - Math.min(fastestMaximum, 40) * 200);
    }

    private Integer checkoutCapability(Offer offer) {
        if (offer.checkoutUrl() != null) {
            return 10_000;
        }
        Boolean capable = offer.rankingEvidence().checkoutCapable();
        return capable == null ? null : capable ? 10_000 : 0;
    }

    private int freshness(Offer offer, Instant rankedAt) {
        Instant latest = offer.provenance().stream()
                .map(ResultProvenance::freshness)
                .map(value -> value.observedAt())
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
        if (latest.isAfter(rankedAt)) {
            return 10_000;
        }
        long ageHours = Math.max(0, Duration.between(latest, rankedAt).toHours());
        return Math.max(0, 10_000 - (int) Math.min(10_000, ageHours * 14));
    }

    private int completeness(List<OfferRankingExplanation.Feature> features) {
        long available = features.stream()
                .filter(feature -> feature.name() != OfferRankingExplanation.Name.DATA_COMPLETENESS)
                .filter(feature -> feature.availability() == OfferRankingExplanation.Availability.AVAILABLE)
                .count();
        return (int) Math.round(available * 10_000.0d / 9.0d);
    }

    private int weightedScore(List<OfferRankingExplanation.Feature> features) {
        long weighted = 0;
        int weights = 0;
        for (OfferRankingExplanation.Feature feature : features) {
            if (feature.availability() == OfferRankingExplanation.Availability.AVAILABLE && feature.weight() > 0) {
                weighted += (long) feature.valueBasisPoints() * feature.weight();
                weights += feature.weight();
            }
        }
        return weights == 0 ? 5_000 : (int) Math.round(weighted / (double) weights);
    }

    private Long landedPrice(Offer offer) {
        if (offer.price() == null) {
            return null;
        }
        OptionalLong deliveryCost = offer.delivery().stream()
                .map(OfferDelivery::cost)
                .filter(java.util.Objects::nonNull)
                .filter(cost -> cost.currency().equals(offer.price().currency()))
                .mapToLong(Money::minorUnits)
                .min();
        if (deliveryCost.isEmpty()) {
            return null;
        }
        try {
            return Math.addExact(offer.price().minorUnits(), deliveryCost.getAsLong());
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private Map<String, Range> ranges(Map<CurrencyAmount, Long> amounts) {
        Map<String, Range> ranges = new HashMap<>();
        amounts.forEach((key, amount) -> ranges.merge(
                key.currency(),
                new Range(amount, amount),
                Range::merge
        ));
        return ranges;
    }

    public record Result(List<Offer> offers, Map<String, OfferRankingExplanation> explanations) {

        public Result {
            offers = offers == null ? List.of() : List.copyOf(offers);
            explanations = explanations == null ? Map.of() : Map.copyOf(explanations);
        }
    }

    private record CurrencyAmount(String offerKey, String currency) {
    }

    private record Range(long minimum, long maximum) {

        private Range merge(Range other) {
            return new Range(Math.min(minimum, other.minimum), Math.max(maximum, other.maximum));
        }

        private int lowerIsBetter(long value) {
            if (minimum == maximum) {
                return 10_000;
            }
            return BigDecimal.valueOf(maximum)
                    .subtract(BigDecimal.valueOf(value))
                    .multiply(BigDecimal.valueOf(10_000))
                    .divide(
                            BigDecimal.valueOf(maximum).subtract(BigDecimal.valueOf(minimum)),
                            0,
                            RoundingMode.HALF_UP
                    )
                    .intValueExact();
        }
    }

    private record ScoredOffer(Offer offer, int scoreBasisPoints, OfferRankingExplanation explanation) {
    }
}

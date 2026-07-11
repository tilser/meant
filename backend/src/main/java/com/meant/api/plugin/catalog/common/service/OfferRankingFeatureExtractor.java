package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferRankingEvidence;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Extracts offer features using one display currency and one coherent delivery choice. */
@Component
@RequiredArgsConstructor
public class OfferRankingFeatureExtractor {

    private final OfferDeliveryChoicePolicy deliveryPolicy;
    private final RankingFreshnessScorer freshnessScorer;
    private final RankingScorePolicy scorePolicy;

    List<ScoredOffer> score(List<Offer> offers, ProductRankingContext context) {
        String currency = displayCurrency(context);
        Map<String, OfferDeliveryChoicePolicy.Choice> choices = new HashMap<>();
        Map<String, Long> items = new HashMap<>();
        Map<String, Long> landed = new HashMap<>();
        for (Offer offer : offers) {
            OfferDeliveryChoicePolicy.Choice choice = deliveryPolicy.choose(offer, context);
            choices.put(offer.key(), choice);
            if (offer.price() != null && currency != null && currency.equals(offer.price().currency())) items.put(offer.key(), offer.price().minorUnits());
            if (choice.landedPrice() != null) landed.put(offer.key(), choice.landedPrice());
        }
        Range itemRange = Range.of(items.values());
        Range landedRange = Range.of(landed.values());
        return offers.stream().map(offer -> score(
                offer, context, choices.get(offer.key()), items.get(offer.key()), itemRange,
                landed.get(offer.key()), landedRange)).toList();
    }

    private ScoredOffer score(Offer offer, ProductRankingContext context, OfferDeliveryChoicePolicy.Choice choice,
                              Long item, Range itemRange, Long landed, Range landedRange) {
        List<OfferRankingExplanation.Feature> features = new ArrayList<>();
        add(features, OfferRankingExplanation.Name.LANDED_PRICE, value(landedRange, landed), 25);
        add(features, OfferRankingExplanation.Name.ITEM_PRICE, value(itemRange, item), landed == null ? 15 : 0);
        add(features, OfferRankingExplanation.Name.AVAILABILITY, availability(offer), 30);
        add(features, OfferRankingExplanation.Name.DELIVERY_EVIDENCE, choice.deliveryScore(), 10);
        OfferRankingEvidence evidence = offer.rankingEvidence();
        add(features, OfferRankingExplanation.Name.MERCHANT_TRUST, evidence.merchantTrustBasisPoints(), 15);
        add(features, OfferRankingExplanation.Name.HISTORICAL_RELIABILITY, evidence.historicalReliabilityBasisPoints(), 25);
        add(features, OfferRankingExplanation.Name.RETURN_POLICY, evidence.returnPolicyBasisPoints(), 10);
        add(features, OfferRankingExplanation.Name.CHECKOUT_CAPABILITY, checkout(offer), 8);
        add(features, OfferRankingExplanation.Name.FRESHNESS, freshnessScorer.score(offer.provenance(), context.rankedAt()), 7);
        add(features, OfferRankingExplanation.Name.DATA_COMPLETENESS, completeness(features), 5);
        int score = scorePolicy.offerScore(features);
        return new ScoredOffer(offer, score, new OfferRankingExplanation(
                OfferRankingService.OFFER_RANKING_VERSION, score, 1,
                OfferRankingExplanation.CommercialTieBreakPolicy.NONE, offer.key(), features));
    }

    private Integer availability(Offer offer) {
        return switch (offer.availability().status()) {
            case IN_STOCK -> 10_000; case PREORDER -> 7_500; case BACKORDER -> 6_000;
            case OUT_OF_STOCK -> 1_000; case DISCONTINUED -> 0; case UNKNOWN -> null;
        };
    }

    private Integer checkout(Offer offer) {
        if (offer.checkoutUrl() != null) return 10_000;
        Boolean capable = offer.rankingEvidence().checkoutCapable();
        return capable == null ? null : capable ? 10_000 : 0;
    }

    private int completeness(List<OfferRankingExplanation.Feature> features) {
        long known = features.stream().filter(feature -> feature.name() != OfferRankingExplanation.Name.DATA_COMPLETENESS)
                .filter(feature -> feature.availability() == OfferRankingExplanation.Availability.AVAILABLE).count();
        return (int) Math.round(known * 10_000.0d / 9.0d);
    }

    private Integer value(Range range, Long amount) { return range == null || amount == null ? null : range.lowerIsBetter(amount); }

    private void add(List<OfferRankingExplanation.Feature> features, OfferRankingExplanation.Name name, Integer value, int weight) {
        features.add(value == null ? OfferRankingExplanation.Feature.unknown(name, weight)
                : OfferRankingExplanation.Feature.available(name, value, weight));
    }

    private String displayCurrency(ProductRankingContext context) {
        return context.searchContext() == null || context.searchContext().currency() == null
                ? null : context.searchContext().currency().trim().toUpperCase(Locale.ROOT);
    }

    record ScoredOffer(Offer offer, int scoreBasisPoints, OfferRankingExplanation explanation) { }

    private record Range(long minimum, long maximum) {
        static Range of(java.util.Collection<Long> values) {
            return values.isEmpty() ? null : new Range(values.stream().min(Long::compareTo).orElseThrow(), values.stream().max(Long::compareTo).orElseThrow());
        }
        int lowerIsBetter(long value) {
            if (minimum == maximum) return 10_000;
            return BigDecimal.valueOf(maximum).subtract(BigDecimal.valueOf(value)).multiply(BigDecimal.valueOf(10_000))
                    .divide(BigDecimal.valueOf(maximum).subtract(BigDecimal.valueOf(minimum)), 0, RoundingMode.HALF_UP).intValueExact();
        }
    }
}

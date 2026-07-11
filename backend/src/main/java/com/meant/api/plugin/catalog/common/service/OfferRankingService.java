package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.Offer;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingContext;
import com.meant.api.plugin.catalog.common.service.OfferRankingFeatureExtractor.ScoredOffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Sorts exact offer objects from independently extracted provider-neutral features. */
@Service
@RequiredArgsConstructor
public class OfferRankingService {

    public static final String OFFER_RANKING_VERSION = "offer-v2";
    private final OfferRankingFeatureExtractor featureExtractor;

    Result rank(List<Offer> offers, ProductRankingContext context) {
        List<ScoredOffer> scored = featureExtractor.score(
                        offers == null ? List.of() : offers.stream().filter(java.util.Objects::nonNull).toList(), context)
                .stream().sorted(Comparator.comparingInt(ScoredOffer::scoreBasisPoints).reversed()
                        .thenComparing(entry -> entry.offer().key())).toList();
        Map<String, OfferRankingExplanation> explanations = new LinkedHashMap<>();
        List<Offer> ranked = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            ScoredOffer entry = scored.get(index);
            ranked.add(entry.offer());
            explanations.put(entry.offer().key(), entry.explanation().withFinalRank(index + 1));
        }
        return new Result(ranked, explanations);
    }

    record Result(List<Offer> offers, Map<String, OfferRankingExplanation> explanations) {
        Result { offers = List.copyOf(offers); explanations = Map.copyOf(explanations); }
    }
}

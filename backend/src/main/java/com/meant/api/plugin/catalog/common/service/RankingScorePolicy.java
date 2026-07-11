package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import java.util.List;
import org.springframework.stereotype.Component;

/** Fixed-denominator missing-data policy; UNKNOWN is explicit and conservatively scored. */
@Component
public class RankingScorePolicy {

    public static final int UNKNOWN_PRIOR_BASIS_POINTS = 1_000;

    int productScore(List<ProductRankingExplanation.Feature> features) {
        long weighted = 0;
        int weights = 0;
        for (ProductRankingExplanation.Feature feature : features) {
            if (feature.weight() <= 0) {
                continue;
            }
            int value = feature.availability() == ProductRankingExplanation.Availability.AVAILABLE
                    ? feature.valueBasisPoints()
                    : UNKNOWN_PRIOR_BASIS_POINTS;
            weighted += (long) value * feature.weight();
            weights += feature.weight();
        }
        return score(weighted, weights);
    }

    int offerScore(List<OfferRankingExplanation.Feature> features) {
        long weighted = 0;
        int weights = 0;
        for (OfferRankingExplanation.Feature feature : features) {
            if (feature.weight() <= 0) {
                continue;
            }
            int value = feature.availability() == OfferRankingExplanation.Availability.AVAILABLE
                    ? feature.valueBasisPoints()
                    : UNKNOWN_PRIOR_BASIS_POINTS;
            weighted += (long) value * feature.weight();
            weights += feature.weight();
        }
        return score(weighted, weights);
    }

    private int score(long weighted, int weights) {
        return weights == 0 ? UNKNOWN_PRIOR_BASIS_POINTS : (int) Math.round(weighted / (double) weights);
    }
}

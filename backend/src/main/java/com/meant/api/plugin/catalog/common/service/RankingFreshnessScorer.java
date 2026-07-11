package com.meant.api.plugin.catalog.common.service;

import com.meant.api.plugin.catalog.common.dto.ResultFreshness;
import com.meant.api.plugin.catalog.common.dto.ResultProvenance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Scores each observation independently; explicit expiry overrides recency for that observation. */
@Component
public class RankingFreshnessScorer {

    private static final long FALLBACK_HORIZON_HOURS = Duration.ofDays(30).toHours();

    int score(List<ResultProvenance> provenance, Instant rankedAt) {
        return provenance.stream()
                .map(ResultProvenance::freshness)
                .mapToInt(freshness -> score(freshness, rankedAt))
                .max()
                .orElse(0);
    }

    private int score(ResultFreshness freshness, Instant rankedAt) {
        if (freshness.freshUntil() != null) {
            return rankedAt.isBefore(freshness.freshUntil()) ? 10_000 : 0;
        }
        if (freshness.observedAt().isAfter(rankedAt)) {
            return 10_000;
        }
        long ageHours = Math.max(0, Duration.between(freshness.observedAt(), rankedAt).toHours());
        return Math.max(0, 10_000 - (int) Math.min(
                10_000,
                ageHours * 10_000 / FALLBACK_HORIZON_HOURS
        ));
    }
}

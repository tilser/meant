package com.meant.api.module.catalog.service;

import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import java.util.List;
import java.util.Map;

/** Optional provider-neutral model boundary; deterministic ranking remains authoritative on failure. */
public interface ProductRankingModel {

    String version();

    Map<String, Integer> rerank(List<Candidate> candidates);

    record Candidate(
            String canonicalProductKey,
            int deterministicScoreBasisPoints,
            List<ProductRankingExplanation.Feature> features
    ) {

        public Candidate {
            features = features == null ? List.of() : List.copyOf(features);
        }
    }
}

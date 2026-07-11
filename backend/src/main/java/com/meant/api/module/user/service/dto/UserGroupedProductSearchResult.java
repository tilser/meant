package com.meant.api.module.user.service.dto;

import com.meant.api.plugin.catalog.common.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.OfferRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductRankingExplanation;
import com.meant.api.plugin.catalog.common.dto.ProductGroupingDecision;
import java.util.List;
import java.util.Map;

public record UserGroupedProductSearchResult(
        String query,
        String normalizedQuery,
        String profileHash,
        boolean cached,
        int offset,
        int limit,
        Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        List<CanonicalProduct> products,
        Map<String, ProductRankingExplanation> productRankingExplanations,
        Map<String, OfferRankingExplanation> offerRankingExplanations,
        int groupingDecisionCount,
        boolean groupingDecisionsTruncated,
        List<ProductGroupingDecision> groupingDecisions
) {

    public UserGroupedProductSearchResult {
        products = products == null ? List.of() : List.copyOf(products);
        productRankingExplanations = productRankingExplanations == null
                ? Map.of() : Map.copyOf(productRankingExplanations);
        offerRankingExplanations = offerRankingExplanations == null
                ? Map.of() : Map.copyOf(offerRankingExplanations);
        groupingDecisions = groupingDecisions == null ? List.of() : List.copyOf(groupingDecisions);
    }

    public UserGroupedProductSearchResult(
            String query,
            String normalizedQuery,
            String profileHash,
            boolean cached,
            int offset,
            int limit,
            Integer nextOffset,
            boolean hasMore,
            boolean upstreamTruncated,
            List<CanonicalProduct> products,
            int groupingDecisionCount,
            boolean groupingDecisionsTruncated,
            List<ProductGroupingDecision> groupingDecisions
    ) {
        this(
                query,
                normalizedQuery,
                profileHash,
                cached,
                offset,
                limit,
                nextOffset,
                hasMore,
                upstreamTruncated,
                products,
                Map.of(),
                Map.of(),
                groupingDecisionCount,
                groupingDecisionsTruncated,
                groupingDecisions
        );
    }
}

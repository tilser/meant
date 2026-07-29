package com.meant.api.module.user.service.dto;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.OfferRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductRankingExplanation;
import com.meant.api.module.catalog.service.dto.ProductGroupingDecision;
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
        Map<String, UserCanonicalProductPersonalizationResult> productPersonalizations,
        List<UserCatalogSourceState> sourceStates,
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
        productPersonalizations = productPersonalizations == null
                ? Map.of() : Map.copyOf(productPersonalizations);
        sourceStates = sourceStates == null ? List.of() : List.copyOf(sourceStates);
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
            Map<String, ProductRankingExplanation> productRankingExplanations,
            Map<String, OfferRankingExplanation> offerRankingExplanations,
            int groupingDecisionCount,
            boolean groupingDecisionsTruncated,
            List<ProductGroupingDecision> groupingDecisions
    ) {
        this(
                query, normalizedQuery, profileHash, cached, offset, limit, nextOffset, hasMore,
                upstreamTruncated, products, productRankingExplanations, offerRankingExplanations,
                Map.of(), List.of(), groupingDecisionCount, groupingDecisionsTruncated, groupingDecisions);
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
                Map.of(),
                List.of(),
                groupingDecisionCount,
                groupingDecisionsTruncated,
                groupingDecisions
        );
    }

}

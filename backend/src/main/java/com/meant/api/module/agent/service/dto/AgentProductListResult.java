package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;
import java.util.Map;

public record AgentProductListResult(
        List<AgentProductReferenceResult> products,
        Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        List<String> unavailableCanonicalProductKeys,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentSimilarityAnchorResult similarityAnchor,
        List<String> searchAdjustments,
        Map<String, AgentAppliedSearchFilter> appliedFilters,
        List<UserProductSearchQuestionTarget> unsetFilters,
        Integer resultCount
) {

    public AgentProductListResult {
        products = products == null ? List.of() : List.copyOf(products);
        unavailableCanonicalProductKeys = unavailableCanonicalProductKeys == null
                ? List.of() : List.copyOf(unavailableCanonicalProductKeys);
        searchAdjustments = searchAdjustments == null ? List.of() : List.copyOf(searchAdjustments);
        appliedFilters = appliedFilters == null ? Map.of() : Map.copyOf(appliedFilters);
        unsetFilters = unsetFilters == null ? List.of() : List.copyOf(unsetFilters);
        resultCount = resultCount == null ? products.size() : Math.max(0, resultCount);
    }

    public AgentProductListResult(
            List<AgentProductReferenceResult> products,
            Integer nextOffset,
            boolean hasMore,
            boolean upstreamTruncated,
            List<String> unavailableCanonicalProductKeys,
            AgentSimilarityAnchorResult similarityAnchor,
            List<String> searchAdjustments
    ) {
        this(
                products,
                nextOffset,
                hasMore,
                upstreamTruncated,
                unavailableCanonicalProductKeys,
                similarityAnchor,
                searchAdjustments,
                Map.of(),
                List.of(),
                products == null ? 0 : products.size()
        );
    }

    public AgentProductListResult(
            List<AgentProductReferenceResult> products,
            Integer nextOffset,
            boolean hasMore,
            boolean upstreamTruncated,
            List<String> unavailableCanonicalProductKeys,
            AgentSimilarityAnchorResult similarityAnchor
    ) {
        this(
                products,
                nextOffset,
                hasMore,
                upstreamTruncated,
                unavailableCanonicalProductKeys,
                similarityAnchor,
                List.of()
        );
    }
}

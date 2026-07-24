package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record AgentProductListResult(
        List<AgentProductReferenceResult> products,
        Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        List<String> unavailableCanonicalProductKeys,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentSimilarityAnchorResult similarityAnchor,
        List<String> searchAdjustments
) {
    public AgentProductListResult {
        products = products == null ? List.of() : List.copyOf(products);
        unavailableCanonicalProductKeys = unavailableCanonicalProductKeys == null
                ? List.of() : List.copyOf(unavailableCanonicalProductKeys);
        searchAdjustments = searchAdjustments == null ? List.of() : List.copyOf(searchAdjustments);
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

package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.module.user.constant.UserProductSearchQuestionTarget;
import java.util.List;

public record AgentProductListResult(
        List<AgentProductReferenceResult> products,
        Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        List<String> unavailableCanonicalProductKeys,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentSimilarityAnchorResult similarityAnchor,
        List<String> searchAdjustments,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String qualificationQuestion,
        List<UserProductSearchQuestionTarget> qualificationTargets
) {
    public AgentProductListResult {
        products = products == null ? List.of() : List.copyOf(products);
        unavailableCanonicalProductKeys = unavailableCanonicalProductKeys == null
                ? List.of() : List.copyOf(unavailableCanonicalProductKeys);
        searchAdjustments = searchAdjustments == null ? List.of() : List.copyOf(searchAdjustments);
        qualificationTargets = qualificationTargets == null ? List.of() : List.copyOf(qualificationTargets);
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
                null,
                List.of()
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
                List.of(),
                null,
                List.of()
        );
    }
}

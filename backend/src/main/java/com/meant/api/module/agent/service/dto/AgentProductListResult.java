package com.meant.api.module.agent.service.dto;

import java.util.List;

public record AgentProductListResult(
        List<AgentProductReferenceResult> products,
        Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        List<String> unavailableCanonicalProductKeys
) {
    public AgentProductListResult {
        products = products == null ? List.of() : List.copyOf(products);
        unavailableCanonicalProductKeys = unavailableCanonicalProductKeys == null
                ? List.of() : List.copyOf(unavailableCanonicalProductKeys);
    }
}

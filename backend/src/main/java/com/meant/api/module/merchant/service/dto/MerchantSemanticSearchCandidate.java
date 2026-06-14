package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantSemanticSearchCandidate(
        UUID merchantId,
        String domain,
        String name,
        String advertisedMcpEndpoint,
        String profileMcpEndpoint,
        String retrievalContent,
        double score
) {
}

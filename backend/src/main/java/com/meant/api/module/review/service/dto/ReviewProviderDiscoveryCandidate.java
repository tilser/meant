package com.meant.api.module.review.service.dto;

import java.util.UUID;

public record ReviewProviderDiscoveryCandidate(
        UUID merchantId,
        String merchantDomain
) {
}

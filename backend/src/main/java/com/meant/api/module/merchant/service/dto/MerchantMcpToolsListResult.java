package com.meant.api.module.merchant.service.dto;

import java.time.Instant;

public record MerchantMcpToolsListResult(
        String endpoint,
        String toolsListRaw,
        String toolsListHash,
        String agentProfileHash,
        Instant capturedAt
) {
}

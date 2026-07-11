package com.meant.api.module.merchant.service.dto;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded routing/capability-only view of a merchant UCP profile. */
public record MerchantUcpProfileObservation(
        String domain,
        URI profileEndpoint,
        Map<String, List<UcpServiceDefinition>> services,
        Set<String> capabilities,
        Instant capturedAt
) {
}

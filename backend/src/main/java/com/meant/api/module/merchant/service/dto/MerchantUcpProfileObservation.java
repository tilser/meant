package com.meant.api.module.merchant.service.dto;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded typed view of a merchant UCP profile used for routing and verified identity resolution. */
public record MerchantUcpProfileObservation(
        String domain,
        URI profileEndpoint,
        UcpProfile profile,
        Map<String, List<UcpServiceDefinition>> services,
        Set<String> capabilities,
        Instant capturedAt
) {
}

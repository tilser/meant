package com.meant.api.module.catalog.service.dto;

import java.util.Set;

public record CommercialFreshnessDecision(
        CommercialFreshnessStatus status,
        Set<CommercialFact> refreshRequired
) {
    public CommercialFreshnessDecision {
        refreshRequired = refreshRequired == null ? Set.of() : Set.copyOf(refreshRequired);
        if ((status == CommercialFreshnessStatus.CURRENT) != refreshRequired.isEmpty()) {
            throw new IllegalArgumentException("Current facts cannot require refresh");
        }
    }
}

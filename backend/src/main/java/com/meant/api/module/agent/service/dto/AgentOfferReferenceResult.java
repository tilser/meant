package com.meant.api.module.agent.service.dto;

import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import java.util.List;

public record AgentOfferReferenceResult(
        String offerKey,
        String merchant,
        String variant,
        Long priceMinorUnits,
        String currency,
        OfferAvailabilityStatus availability,
        List<ProductAttribute> selectedOptions
) {
    public AgentOfferReferenceResult {
        selectedOptions = selectedOptions == null ? List.of() : List.copyOf(selectedOptions);
    }
}

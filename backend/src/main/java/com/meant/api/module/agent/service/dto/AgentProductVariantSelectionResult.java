package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record AgentProductVariantSelectionResult(
        String canonicalProductKey,
        String anchorOfferKey,
        boolean exactMatch,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String selectedOfferKey,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentOfferReferenceResult selectedOffer,
        boolean cartable,
        AgentProductVariantDetailsResult variantDetails
) {
}

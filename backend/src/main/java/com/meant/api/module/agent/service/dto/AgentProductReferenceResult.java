package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record AgentProductReferenceResult(
        int reference,
        String canonicalProductKey,
        String title,
        String description,
        String imageUrl,
        String recommendedOfferKey,
        List<AgentOfferReferenceResult> offers,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentProductVariantDetailsResult variantDetails
) {
    public AgentProductReferenceResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    public AgentProductReferenceResult(
            int reference,
            String canonicalProductKey,
            String title,
            String description,
            String imageUrl,
            String recommendedOfferKey,
            List<AgentOfferReferenceResult> offers
    ) {
        this(
                reference,
                canonicalProductKey,
                title,
                description,
                imageUrl,
                recommendedOfferKey,
                offers,
                null
        );
    }
}

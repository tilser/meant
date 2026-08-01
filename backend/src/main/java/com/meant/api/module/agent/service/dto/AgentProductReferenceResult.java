package com.meant.api.module.agent.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.module.user.service.dto.UserCanonicalProductPersonalizationResult;
import java.util.List;

public record AgentProductReferenceResult(
        int reference,
        String canonicalProductKey,
        String title,
        String description,
        String imageUrl,
        String recommendedOfferKey,
        List<AgentOfferReferenceResult> offers,
        UserCanonicalProductPersonalizationResult personalization,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        AgentProductVariantDetailsResult variantDetails
) {
    public AgentProductReferenceResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
        personalization = personalization == null
                ? UserCanonicalProductPersonalizationResult.searchRelevance()
                : personalization;
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
                UserCanonicalProductPersonalizationResult.searchRelevance(),
                null
        );
    }

    public AgentProductReferenceResult(
            int reference,
            String canonicalProductKey,
            String title,
            String description,
            String imageUrl,
            String recommendedOfferKey,
            List<AgentOfferReferenceResult> offers,
            AgentProductVariantDetailsResult variantDetails
    ) {
        this(
                reference,
                canonicalProductKey,
                title,
                description,
                imageUrl,
                recommendedOfferKey,
                offers,
                UserCanonicalProductPersonalizationResult.searchRelevance(),
                variantDetails
        );
    }
}

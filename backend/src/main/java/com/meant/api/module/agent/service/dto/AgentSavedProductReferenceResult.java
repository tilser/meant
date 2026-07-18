package com.meant.api.module.agent.service.dto;

import com.meant.api.module.user.service.dto.UserSavedProductResult;
import java.util.List;

public record AgentSavedProductReferenceResult(
        int reference,
        String productKey,
        String name,
        String brand,
        String category,
        String imageUrl,
        Integer match,
        Long priceFromMinorUnits,
        String priceCurrency,
        List<UserSavedProductResult.Offer> offers
) {
    public AgentSavedProductReferenceResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }
}

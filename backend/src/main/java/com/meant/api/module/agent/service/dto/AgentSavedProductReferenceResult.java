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
        List<AgentSavedProductOfferResult> offers
) {
    public AgentSavedProductReferenceResult {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    public static AgentSavedProductReferenceResult from(
            UserSavedProductResult product,
            int ordinal
    ) {
        String merchantOrigin = product.offers().stream()
                .map(UserSavedProductResult.Offer::merchantOrigin)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> product.details() != null && product.details().merchantOrigin() != null
                        ? product.details().merchantOrigin()
                        : product.merchantOrigin());
        List<String> technicalAliases = java.util.stream.Stream.concat(
                        product.technicalEndpointAliases().stream(),
                        product.details() == null
                                ? java.util.stream.Stream.empty()
                                : product.details().technicalEndpointAliases().stream()
                )
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        AgentBuyerDisplayText.Context context =
                AgentBuyerDisplayText.context(merchantOrigin, technicalAliases);
        return new AgentSavedProductReferenceResult(
                ordinal,
                product.id(),
                AgentBuyerDisplayText.label(product.name(), context),
                AgentBuyerDisplayText.label(product.brand(), context),
                AgentBuyerDisplayText.label(product.category(), context),
                AgentBuyerDisplayText.safeUrl(product.imageUrl(), context),
                product.match(),
                product.priceFromMinorUnits(),
                product.priceCurrency(),
                product.offers().stream()
                        .map(offer -> AgentSavedProductOfferResult.from(
                                offer,
                                AgentBuyerDisplayText.context(
                                        offer.merchantOrigin(),
                                        technicalAliases
                                )
                        ))
                        .toList()
        );
    }
}

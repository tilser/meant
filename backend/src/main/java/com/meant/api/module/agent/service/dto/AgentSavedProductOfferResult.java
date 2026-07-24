package com.meant.api.module.agent.service.dto;

import com.meant.api.module.user.service.dto.UserSavedProductResult;

/**
 * Buyer-visible saved-offer facts. Provider routing domains remain on the internal saved reference.
 */
public record AgentSavedProductOfferResult(
        String offerKey,
        String merchant,
        Double price,
        Long priceMinorUnits,
        String priceCurrency,
        String delivery,
        String merchantId,
        String merchantOrigin,
        String productVariantId,
        String variantTitle,
        Boolean available
) {

    public static AgentSavedProductOfferResult from(UserSavedProductResult.Offer offer) {
        return from(offer, AgentBuyerDisplayText.context(offer.merchantOrigin(), null));
    }

    static AgentSavedProductOfferResult from(
            UserSavedProductResult.Offer offer,
            AgentBuyerDisplayText.Context context
    ) {
        return new AgentSavedProductOfferResult(
                offer.offerKey(),
                AgentBuyerDisplayText.label(offer.merchant(), context),
                offer.price(),
                offer.priceMinorUnits(),
                offer.priceCurrency(),
                AgentBuyerDisplayText.text(offer.delivery(), context),
                offer.merchantId(),
                context.merchantOrigin(),
                offer.productVariantId(),
                AgentBuyerDisplayText.text(offer.variantTitle(), context),
                offer.available()
        );
    }
}

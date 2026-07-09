package com.meant.api.plugin.checkout.update.dto;

import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record UpdateCheckoutRequest(
        String checkoutId,
        List<LineItem> lineItems,
        Map<String, Object> buyer,
        BuyerConsentState buyerConsent,
        String email,
        String currency,
        Map<String, Object> context,
        List<String> discountCodes,
        Map<String, Object> fulfillment
) {

    public UpdateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = buyer == null ? Map.of() : new LinkedHashMap<>(buyer);
        context = context == null ? Map.of() : new LinkedHashMap<>(context);
        discountCodes = discountCodes == null ? List.of() : List.copyOf(discountCodes);
        fulfillment = fulfillment == null ? Map.of() : new LinkedHashMap<>(fulfillment);
    }

    public record LineItem(
            String id,
            String productVariantId,
            Integer quantity
    ) {
    }
}

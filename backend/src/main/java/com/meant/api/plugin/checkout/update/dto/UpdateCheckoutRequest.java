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
        Map<String, Object> shippingAddress,
        List<String> discountCodes,
        Map<String, Object> fulfillment
) {

    public UpdateCheckoutRequest(
            String checkoutId,
            Map<String, Object> buyer,
            String email,
            Map<String, Object> shippingAddress
    ) {
        this(checkoutId, List.of(), buyer, null, email, null, shippingAddress, List.of(), Map.of());
    }

    public UpdateCheckoutRequest(
            String checkoutId,
            Map<String, Object> buyer,
            String email,
            Map<String, Object> shippingAddress,
            List<String> discountCodes,
            Map<String, Object> fulfillment
    ) {
        this(checkoutId, List.of(), buyer, null, email, null, shippingAddress, discountCodes, fulfillment);
    }

    public UpdateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = buyer == null ? Map.of() : new LinkedHashMap<>(buyer);
        shippingAddress = shippingAddress == null ? Map.of() : new LinkedHashMap<>(shippingAddress);
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

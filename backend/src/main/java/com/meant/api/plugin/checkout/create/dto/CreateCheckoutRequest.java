package com.meant.api.plugin.checkout.create.dto;

import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerConsentState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CreateCheckoutRequest(
        String cartId,
        List<LineItem> lineItems,
        Map<String, Object> buyer,
        BuyerConsentState buyerConsent,
        String currency,
        List<String> discountCodes,
        Map<String, Object> fulfillment
) {

    public CreateCheckoutRequest(String cartId) {
        this(cartId, List.of());
    }

    public CreateCheckoutRequest(String cartId, List<LineItem> lineItems) {
        this(cartId, lineItems, Map.of(), null, null, List.of(), Map.of());
    }

    public CreateCheckoutRequest(
            String cartId,
            List<LineItem> lineItems,
            Map<String, Object> buyer,
            List<String> discountCodes,
            Map<String, Object> fulfillment
    ) {
        this(cartId, lineItems, buyer, null, null, discountCodes, fulfillment);
    }

    public CreateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = buyer == null ? Map.of() : new LinkedHashMap<>(buyer);
        discountCodes = discountCodes == null ? List.of() : List.copyOf(discountCodes);
        fulfillment = fulfillment == null ? Map.of() : new LinkedHashMap<>(fulfillment);
    }

    public record LineItem(
            String id,
            String productVariantId,
            Integer quantity
    ) {
        public LineItem(String productVariantId, Integer quantity) {
            this(null, productVariantId, quantity);
        }
    }
}

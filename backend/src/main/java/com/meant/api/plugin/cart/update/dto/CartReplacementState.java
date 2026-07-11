package com.meant.api.plugin.cart.update.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/** Complete desired cart state for PUT-style provider updates. */
public record CartReplacementState(
        List<CartAddItem> lineItems,
        Map<String, Object> buyer,
        Map<String, Object> context,
        Map<String, Object> signals,
        CartToolArguments.Fulfillment fulfillment,
        CartToolArguments.Discounts discounts,
        List<String> giftCardCodes,
        String note
) {
    public CartReplacementState {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        buyer = immutableMap(buyer);
        context = immutableMap(context);
        signals = immutableMap(signals);
        giftCardCodes = giftCardCodes == null ? List.of() : List.copyOf(giftCardCodes);
    }

    public CartToolArguments arguments() {
        return CartToolArguments.replacement(
                lineItems, buyer, context, signals, fulfillment, discounts, giftCardCodes, note);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> values) {
        return values == null || values.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

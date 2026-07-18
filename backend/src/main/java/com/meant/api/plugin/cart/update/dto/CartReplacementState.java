package com.meant.api.plugin.cart.update.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartSignals;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;
import java.util.List;

/** Complete desired cart state for PUT-style provider updates. */
public record CartReplacementState(
        List<CartAddItem> lineItems,
        CartBuyer buyer,
        CartContext context,
        CartSignals signals,
        CartToolArguments.Fulfillment fulfillment,
        CartToolArguments.Discounts discounts,
        List<String> giftCardCodes,
        String note
) {
    public CartReplacementState {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
        giftCardCodes = giftCardCodes == null ? List.of() : List.copyOf(giftCardCodes);
    }

    public CartToolArguments arguments() {
        return CartToolArguments.replacement(
                lineItems, buyer, context, signals, fulfillment, discounts, giftCardCodes, note);
    }
}

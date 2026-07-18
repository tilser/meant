package com.meant.api.plugin.cart.update.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import java.util.List;

public record UpdateCartRequest(
        String cartId,
        List<CartAddItem> addItems,
        List<CartUpdateItem> updateItems,
        List<String> removeLineIds,
        List<CartUpdateItem> removeItems,
        CartBuyer buyerIdentity,
        CartContext context,
        List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
        List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
        List<CartDeliveryOptionSelection> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note,
        CartReplacementState replacementState
) {

    public UpdateCartRequest(
            String cartId, List<CartAddItem> addItems, List<CartUpdateItem> updateItems,
            List<String> removeLineIds, List<CartUpdateItem> removeItems, CartBuyer buyerIdentity,
            CartContext context, List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
            List<String> discountCodes, List<String> giftCardCodes, String note
    ) {
        this(cartId, addItems, updateItems, removeLineIds, removeItems, buyerIdentity, context,
                deliveryAddressesToAdd, deliveryAddressesToReplace, selectedDeliveryOptions,
                discountCodes, giftCardCodes, note, null);
    }

    public UpdateCartRequest(
            String cartId,
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<String> removeLineIds,
            List<CartUpdateItem> removeItems,
            CartBuyer buyerIdentity,
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        this(
                cartId,
                addItems,
                updateItems,
                removeLineIds,
                removeItems,
                buyerIdentity,
                null,
                deliveryAddressesToAdd,
                deliveryAddressesToReplace,
                selectedDeliveryOptions,
                discountCodes,
                giftCardCodes,
                note,
                null
        );
    }

    public UpdateCartRequest(
            String cartId,
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<String> removeLineIds,
            CartBuyer buyerIdentity,
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        this(
                cartId,
                addItems,
                updateItems,
                removeLineIds,
                removeLineIds == null
                        ? null
                        : removeLineIds.stream()
                                .map(lineId -> new CartUpdateItem(lineId, null, 0))
                                .toList(),
                buyerIdentity,
                null,
                deliveryAddressesToAdd,
                deliveryAddressesToReplace,
                selectedDeliveryOptions,
                discountCodes,
                giftCardCodes,
                note,
                null
        );
    }
}

package com.meant.api.plugin.cart.update.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartUpdateItem;
import java.util.List;
import java.util.Map;

public record UpdateCartRequest(
        String cartId,
        List<CartAddItem> addItems,
        List<CartUpdateItem> updateItems,
        List<String> removeLineIds,
        List<CartUpdateItem> removeItems,
        Map<String, Object> buyerIdentity,
        Map<String, Object> context,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {

    public UpdateCartRequest(
            String cartId,
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<String> removeLineIds,
            List<CartUpdateItem> removeItems,
            Map<String, Object> buyerIdentity,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
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
                note
        );
    }

    public UpdateCartRequest(
            String cartId,
            List<CartAddItem> addItems,
            List<CartUpdateItem> updateItems,
            List<String> removeLineIds,
            Map<String, Object> buyerIdentity,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
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
                deliveryAddressesToAdd,
                deliveryAddressesToReplace,
                selectedDeliveryOptions,
                discountCodes,
                giftCardCodes,
                note
        );
    }
}

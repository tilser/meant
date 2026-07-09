package com.meant.api.plugin.cart.create.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import java.util.List;
import java.util.Map;

public record CreateCartRequest(
        List<CartAddItem> addItems,
        Map<String, Object> buyerIdentity,
        Map<String, Object> context,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {

    public CreateCartRequest(
            List<CartAddItem> addItems,
            Map<String, Object> buyerIdentity,
            List<Map<String, Object>> deliveryAddressesToAdd,
            List<Map<String, Object>> deliveryAddressesToReplace,
            List<Map<String, Object>> selectedDeliveryOptions,
            List<String> discountCodes,
            List<String> giftCardCodes,
            String note
    ) {
        this(
                addItems,
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
}

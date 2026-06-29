package com.meant.api.plugin.cart.create.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import java.util.List;
import java.util.Map;

public record CreateCartRequest(
        List<CartAddItem> addItems,
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {
}

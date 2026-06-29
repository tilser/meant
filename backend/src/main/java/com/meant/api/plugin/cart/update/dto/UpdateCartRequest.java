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
        Map<String, Object> buyerIdentity,
        List<Map<String, Object>> deliveryAddressesToAdd,
        List<Map<String, Object>> deliveryAddressesToReplace,
        List<Map<String, Object>> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {
}

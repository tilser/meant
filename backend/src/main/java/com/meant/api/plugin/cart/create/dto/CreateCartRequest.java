package com.meant.api.plugin.cart.create.dto;

import com.meant.api.plugin.cart.common.dto.CartAddItem;
import com.meant.api.plugin.cart.common.dto.CartBuyer;
import com.meant.api.plugin.cart.common.dto.CartContext;
import com.meant.api.plugin.cart.common.dto.CartDeliveryAddressSelection;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import java.util.List;

public record CreateCartRequest(
        List<CartAddItem> addItems,
        CartBuyer buyerIdentity,
        CartContext context,
        List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
        List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
        List<CartDeliveryOptionSelection> selectedDeliveryOptions,
        List<String> discountCodes,
        List<String> giftCardCodes,
        String note
) {

    public CreateCartRequest(
            List<CartAddItem> addItems,
            CartBuyer buyerIdentity,
            List<CartDeliveryAddressSelection> deliveryAddressesToAdd,
            List<CartDeliveryAddressSelection> deliveryAddressesToReplace,
            List<CartDeliveryOptionSelection> selectedDeliveryOptions,
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

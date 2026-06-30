package com.meant.api.plugin.checkout.create.dto;

import java.util.List;

public record CreateCheckoutRequest(
        String cartId,
        List<LineItem> lineItems
) {

    public CreateCheckoutRequest(String cartId) {
        this(cartId, List.of());
    }

    public CreateCheckoutRequest {
        lineItems = lineItems == null ? List.of() : List.copyOf(lineItems);
    }

    public record LineItem(
            String productVariantId,
            Integer quantity
    ) {
    }
}

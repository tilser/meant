package com.meant.api.plugin.checkout.create.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCheckoutArguments(
        Checkout checkout
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Checkout(
            @JsonProperty("cart_id")
            String cartId,
            @JsonProperty("line_items")
            List<LineItem> lineItems,
            Map<String, Object> buyer,
            String currency,
            Map<String, Object> context,
            CheckoutDiscounts discounts,
            CheckoutFulfillment fulfillment
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LineItem(
            Item item,
            Integer quantity
    ) {
    }

    public record Item(
            String id
    ) {
    }
}

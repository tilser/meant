package com.meant.api.plugin.checkout.create.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerWithConsent;
import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.util.List;

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
            BuyerWithConsent buyer,
            String currency,
            CheckoutContext context,
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

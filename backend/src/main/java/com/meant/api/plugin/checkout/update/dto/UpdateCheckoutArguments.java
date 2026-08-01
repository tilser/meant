package com.meant.api.plugin.checkout.update.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.common.dto.CheckoutContext;
import com.meant.api.plugin.checkout.extension.buyerconsent.dto.BuyerWithConsent;
import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import com.meant.api.plugin.support.UcpAttribution;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCheckoutArguments(
        @JsonProperty("id")
        String checkoutId,
        Checkout checkout
) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Checkout(
            @JsonProperty("line_items")
            List<LineItem> lineItems,
            BuyerWithConsent buyer,
            String currency,
            CheckoutContext context,
            CheckoutFulfillment fulfillment,
            CheckoutDiscounts discounts,
            UcpAttribution attribution
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LineItem(
            String id,
            Item item,
            Integer quantity
    ) {
    }

    public record Item(
            String id
    ) {
    }
}

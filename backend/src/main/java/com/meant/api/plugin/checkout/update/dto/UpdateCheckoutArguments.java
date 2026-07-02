package com.meant.api.plugin.checkout.update.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.checkout.extension.discount.dto.CheckoutDiscounts;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCheckoutArguments(
        @JsonProperty("checkout_id")
        String checkoutId,
        Map<String, Object> buyer,
        String email,
        CheckoutFulfillment fulfillment,
        CheckoutDiscounts discounts
) {
}

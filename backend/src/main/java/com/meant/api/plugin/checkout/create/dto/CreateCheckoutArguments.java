package com.meant.api.plugin.checkout.create.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCheckoutArguments(
        @JsonProperty("cart_id")
        String cartId
) {
}

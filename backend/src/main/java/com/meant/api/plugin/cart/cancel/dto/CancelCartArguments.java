package com.meant.api.plugin.cart.cancel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CancelCartArguments(
        @JsonProperty("cart_id")
        String cartId
) {
}

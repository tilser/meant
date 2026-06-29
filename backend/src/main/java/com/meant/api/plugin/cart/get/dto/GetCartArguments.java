package com.meant.api.plugin.cart.get.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetCartArguments(
        @JsonProperty("cart_id")
        String cartId
) {
}

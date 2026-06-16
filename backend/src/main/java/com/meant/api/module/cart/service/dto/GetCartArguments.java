package com.meant.api.module.cart.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetCartArguments(
        @JsonProperty("cart_id")
        String cartId
) {
}

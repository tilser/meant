package com.meant.api.plugin.cart.update.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateCartArguments(
        String id,
        CartToolArguments cart
) {
}

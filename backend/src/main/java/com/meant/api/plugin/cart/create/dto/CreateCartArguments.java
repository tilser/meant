package com.meant.api.plugin.cart.create.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meant.api.plugin.cart.common.dto.CartToolArguments;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateCartArguments(
        CartToolArguments cart
) {
}

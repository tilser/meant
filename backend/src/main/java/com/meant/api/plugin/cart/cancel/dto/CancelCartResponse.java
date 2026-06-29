package com.meant.api.plugin.cart.cancel.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelCartResponse(
        @JsonProperty("cart_id")
        @JsonAlias({"cartId", "id"})
        String cartId,
        String status,
        Boolean canceled,
        List<UcpCartResponse.CartMessage> messages,
        List<UcpCartResponse.CartError> errors
) {
}

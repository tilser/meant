package com.meant.api.plugin.order.get.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetOrderArguments(
        @JsonProperty("order_id")
        String orderId
) {
}

package com.meant.api.plugin.checkout.get.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GetCheckoutArguments(
        @JsonProperty("id")
        String checkoutId
) {
}

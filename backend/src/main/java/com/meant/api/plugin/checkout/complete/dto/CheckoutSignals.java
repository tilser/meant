package com.meant.api.plugin.checkout.complete.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutSignals(
        @JsonProperty("dev.meant.checkout_surface")
        String checkoutSurface,
        @JsonProperty("user_agent")
        String userAgent
) {
}

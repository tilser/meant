package com.meant.api.module.cart.service.dto;

import java.util.List;

public record EmbeddedCheckoutConfiguration(
        String protocolVersion,
        List<String> merchantAllowedDelegations,
        String authenticationType
) {
    public EmbeddedCheckoutConfiguration {
        merchantAllowedDelegations = merchantAllowedDelegations == null
                ? List.of() : List.copyOf(merchantAllowedDelegations);
    }
}

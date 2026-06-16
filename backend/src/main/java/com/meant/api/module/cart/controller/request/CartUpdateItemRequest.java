package com.meant.api.module.cart.controller.request;

import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CartUpdateItemRequest(
        UUID cartLineId,
        String remoteCartLineId,
        @Positive
        Integer quantity
) {
}

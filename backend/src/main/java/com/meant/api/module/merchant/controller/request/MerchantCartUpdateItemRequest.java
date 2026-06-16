package com.meant.api.module.merchant.controller.request;

import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record MerchantCartUpdateItemRequest(
        UUID cartLineId,
        String remoteCartLineId,
        @Positive
        Integer quantity
) {
}

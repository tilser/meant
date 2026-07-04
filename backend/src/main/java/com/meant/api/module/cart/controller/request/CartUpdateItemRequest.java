package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record CartUpdateItemRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID cartLineId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String remoteCartLineId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        Integer quantity
) {
}

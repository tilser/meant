package com.meant.api.plugin.checkout.common.service.command;

import com.meant.api.plugin.checkout.common.entity.CheckoutIdempotencyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordIdempotencyResponseCommand(
        @NotBlank String idempotencyKey,
        @NotNull CheckoutIdempotencyStatus status,
        String remoteResponse,
        String finalOrderRef
) {
}

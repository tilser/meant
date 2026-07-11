package com.meant.api.module.checkout.service.command;

import com.meant.api.module.checkout.entity.CheckoutIdempotencyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RecordIdempotencyResponseCommand(
        @NotBlank String idempotencyKey,
        @NotNull CheckoutIdempotencyStatus status,
        String remoteResponse,
        String finalOrderRef
) {
}

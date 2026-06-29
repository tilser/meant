package com.meant.api.plugin.checkout.common.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.UUID;

public record ReserveIdempotencyKeyCommand(
        @NotBlank String idempotencyKey,
        @NotNull byte[] body,
        @NotBlank String checkoutId,
        @NotNull UUID consentId,
        @NotNull Long amount,
        @NotBlank String currency,
        @NotNull UUID merchantId
) {

    public ReserveIdempotencyKeyCommand {
        body = body == null ? null : Arrays.copyOf(body, body.length);
    }

    @Override
    public byte[] body() {
        return body == null ? null : Arrays.copyOf(body, body.length);
    }
}

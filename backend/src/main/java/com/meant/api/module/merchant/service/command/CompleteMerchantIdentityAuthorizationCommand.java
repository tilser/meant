package com.meant.api.module.merchant.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CompleteMerchantIdentityAuthorizationCommand(
        @NotNull UUID userId,
        @NotBlank String state,
        @NotBlank String code,
        String issuer
) {
}

package com.meant.api.module.merchant.service.command;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartMerchantIdentityAuthorizationCommand(
        @NotNull UUID userId,
        @NotNull UUID merchantId
) {
}

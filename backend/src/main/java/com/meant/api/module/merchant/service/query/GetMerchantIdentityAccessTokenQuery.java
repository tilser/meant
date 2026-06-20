package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetMerchantIdentityAccessTokenQuery(
        @NotNull UUID userId,
        @NotNull UUID merchantId
) {
}

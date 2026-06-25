package com.meant.api.module.merchant.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetMerchantProductDetailsQuery(
        @NotNull
        UUID merchantId,

        @NotBlank
        String productId,

        String addressCountry,

        String language
) {
}

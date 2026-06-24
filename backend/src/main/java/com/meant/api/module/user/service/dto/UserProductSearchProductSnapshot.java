package com.meant.api.module.user.service.dto;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;
import jakarta.validation.constraints.NotNull;

public record UserProductSearchProductSnapshot(
        String productKey,
        String productHash,
        @NotNull
        MerchantSemanticProductResult product
) {
}

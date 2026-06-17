package com.meant.api.module.user.service.dto;

import com.meant.api.module.merchant.service.dto.MerchantSemanticProductResult;

public record UserProductSearchProductSnapshot(
        String productKey,
        String productHash,
        MerchantSemanticProductResult product
) {
}

package com.meant.api.module.merchant.service.dto;

import java.util.UUID;

public record MerchantCheckoutResult(
        UUID cartId,
        String remoteCartId,
        String checkoutUrl
) {
}

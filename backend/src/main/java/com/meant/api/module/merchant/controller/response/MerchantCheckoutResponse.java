package com.meant.api.module.merchant.controller.response;

import com.meant.api.module.merchant.service.dto.MerchantCheckoutResult;
import java.util.UUID;

public record MerchantCheckoutResponse(
        UUID cartId,
        String remoteCartId,
        String checkoutUrl
) {

    public static MerchantCheckoutResponse from(MerchantCheckoutResult result) {
        return new MerchantCheckoutResponse(result.cartId(), result.remoteCartId(), result.checkoutUrl());
    }
}

package com.meant.api.module.checkout.service.port;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;

/** Provider-neutral transport boundary for checkout-session tools. */
public interface CheckoutToolTransport {
    boolean supports(CartRoutingTarget target);

    MerchantMcpToolCallResult call(
            CartRoutingTarget target, String toolName, Object arguments, CheckoutToolCallContext context);
}

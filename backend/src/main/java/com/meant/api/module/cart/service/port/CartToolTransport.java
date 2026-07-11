package com.meant.api.module.cart.service.port;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;

/** Provider-owned authenticated transport selected from an immutable cart route. */
public interface CartToolTransport {
    boolean supports(CartRoutingTarget target);

    MerchantMcpToolCallResult call(
            CartRoutingTarget target, String toolName, Object arguments, CartToolCallContext context);
}

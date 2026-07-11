package com.meant.api.module.cart.service;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.checkout.constant.CheckoutLifecycleState;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import org.springframework.stereotype.Component;

@Component
public class CheckoutCancellationPolicy {
    public void requireCancelled(UcpCheckoutResponse response) {
        if (CheckoutLifecycleState.from(response) != CheckoutLifecycleState.CANCELLED) {
            throw CartException.rejected("The merchant did not cancel the checkout session");
        }
    }
}

package com.meant.api.module.checkout.service.port;

import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import java.util.Map;

/** Provider-owned checkout headers contributed without leaking provider wire names into modules. */
public interface CheckoutRequestHeaderContributor {

    boolean supports(MerchantCartProvider provider, CommerceOperation operation);

    Map<String, String> headers(CheckoutToolCallContext context);
}

package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.MerchantCapabilityReadinessContext;

public interface MerchantCapabilityReadinessAdapter {

    MerchantIntegrationProvider provider();

    boolean advertised(CommerceOperation operation, MerchantCapabilityReadinessContext context);

    CapabilityAuthorizationDecision authorization(
            CommerceOperation operation,
            MerchantCapabilityReadinessContext context
    );
}

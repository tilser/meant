package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.MerchantCapabilityReadinessContext;

public interface MerchantCapabilityReadinessAdapter {

    MerchantIntegrationProvider provider();

    /**
     * Allows a concrete platform adapter to contribute a capability surface when a generic UCP
     * integration carries authoritative platform evidence. Transport identity remains unchanged.
     */
    default boolean overridesContext(
            CommerceOperation operation,
            MerchantCapabilityReadinessContext context
    ) {
        return false;
    }

    boolean advertised(CommerceOperation operation, MerchantCapabilityReadinessContext context);

    CapabilityAuthorizationDecision authorization(
            CommerceOperation operation,
            MerchantCapabilityReadinessContext context
    );
}

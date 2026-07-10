package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import java.util.UUID;

public record CapabilityEvaluationInput(
        CommerceOperation operation,
        boolean advertised,
        CapabilityAuthorizationDecision authorization,
        boolean rolloutEnabled,
        CapabilityIntegrationHealth integrationHealth,
        CapabilityFallback fallback,
        CommerceExecutionRail executionRail,
        UUID integrationId,
        MerchantIntegrationProvider provider
) {
}

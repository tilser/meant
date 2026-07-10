package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import java.util.List;
import java.util.UUID;

public record CommerceCapabilityDecision(
        CommerceOperation operation,
        boolean advertised,
        CapabilityAuthorizationDecision authorization,
        boolean rolloutEnabled,
        CapabilityIntegrationHealth integrationHealth,
        boolean fallbackSupported,
        CapabilityAvailability availability,
        CommerceExecutionRail selectedRail,
        List<CapabilityIneligibilityReason> ineligibilityReasons,
        UUID integrationId,
        MerchantIntegrationProvider provider
) {

    public CommerceCapabilityDecision {
        ineligibilityReasons = ineligibilityReasons == null ? List.of() : List.copyOf(ineligibilityReasons);
    }

    public boolean available() {
        return availability == CapabilityAvailability.AVAILABLE;
    }
}

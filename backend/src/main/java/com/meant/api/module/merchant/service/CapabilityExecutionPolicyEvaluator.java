package com.meant.api.module.merchant.service;

import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.service.dto.CapabilityEvaluationInput;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CapabilityExecutionPolicyEvaluator {

    public CommerceCapabilityDecision evaluate(CapabilityEvaluationInput input) {
        List<CapabilityIneligibilityReason> reasons = new ArrayList<>();
        if (!input.advertised()) {
            reasons.add(CapabilityIneligibilityReason.NOT_ADVERTISED);
        }
        authorizationReason(input.authorization().status(), reasons);
        if (!input.rolloutEnabled()) {
            reasons.add(CapabilityIneligibilityReason.ROLLOUT_DISABLED);
        }
        if (input.integrationHealth() != CapabilityIntegrationHealth.HEALTHY) {
            reasons.add(CapabilityIneligibilityReason.INTEGRATION_UNHEALTHY);
        }

        boolean available = input.advertised()
                && input.authorization().ready()
                && input.rolloutEnabled()
                && input.integrationHealth() == CapabilityIntegrationHealth.HEALTHY;
        CapabilityAvailability availability;
        CommerceExecutionRail selectedRail;
        if (available) {
            availability = CapabilityAvailability.AVAILABLE;
            selectedRail = input.executionRail();
        } else if (input.fallback().supported()) {
            availability = CapabilityAvailability.FALLBACK_AVAILABLE;
            selectedRail = input.fallback().rail();
            reasons.add(CapabilityIneligibilityReason.FALLBACK_SELECTED);
        } else {
            availability = CapabilityAvailability.UNAVAILABLE;
            selectedRail = CommerceExecutionRail.NONE;
            reasons.add(CapabilityIneligibilityReason.NO_FALLBACK);
        }

        return new CommerceCapabilityDecision(
                input.operation(),
                input.advertised(),
                input.authorization(),
                input.rolloutEnabled(),
                input.integrationHealth(),
                input.fallback().supported(),
                availability,
                selectedRail,
                reasons,
                input.integrationId(),
                input.provider()
        );
    }

    private void authorizationReason(
            CapabilityAuthorizationStatus status,
            List<CapabilityIneligibilityReason> reasons
    ) {
        switch (status) {
            case NOT_REQUIRED, READY -> {
            }
            case NOT_AUTHORIZED -> reasons.add(CapabilityIneligibilityReason.AUTHORIZATION_REQUIRED);
            case AUTHENTICATION_DISABLED -> reasons.add(CapabilityIneligibilityReason.AUTHENTICATION_DISABLED);
            case TIER_NOT_GRANTED -> reasons.add(CapabilityIneligibilityReason.TIER_NOT_GRANTED);
            case MISSING_SCOPES -> reasons.add(CapabilityIneligibilityReason.MISSING_SCOPES);
            case UNSUPPORTED -> reasons.add(CapabilityIneligibilityReason.OPERATION_UNSUPPORTED);
        }
    }
}

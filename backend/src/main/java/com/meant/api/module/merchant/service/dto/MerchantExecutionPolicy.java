package com.meant.api.module.merchant.service.dto;

import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import java.util.Arrays;
import java.util.List;

public record MerchantExecutionPolicy(
        List<CommerceCapabilityDecision> decisions
) {

    public MerchantExecutionPolicy {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }

    public CommerceCapabilityDecision decision(CommerceOperation operation) {
        return decisions.stream()
                .filter(decision -> decision.operation() == operation)
                .findFirst()
                .orElseGet(() -> unavailableDecision(operation));
    }

    public boolean isAvailable(CommerceOperation operation) {
        return decision(operation).available();
    }

    public static MerchantExecutionPolicy unavailable() {
        return new MerchantExecutionPolicy(
                Arrays.stream(CommerceOperation.values())
                        .map(MerchantExecutionPolicy::unavailableDecision)
                        .toList()
        );
    }

    /**
     * Temporary compatibility bridge for internal callers that have not yet been migrated to an
     * integration-backed policy. Production checkout routing must use evaluated policy decisions.
     */
    @Deprecated(forRemoval = true)
    public static MerchantExecutionPolicy legacyNativeCheckout(boolean enabled) {
        List<CommerceCapabilityDecision> values = Arrays.stream(CommerceOperation.values())
                .map(operation -> enabled && operation == CommerceOperation.DIRECT_CHECKOUT_COMPLETION
                        ? new CommerceCapabilityDecision(
                                CommerceOperation.DIRECT_CHECKOUT_COMPLETION,
                                true,
                                CapabilityAuthorizationDecision.notRequired(),
                                true,
                                CapabilityIntegrationHealth.HEALTHY,
                                true,
                                CapabilityAvailability.AVAILABLE,
                                CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION,
                                List.of(),
                                null,
                                null
                        )
                        : unavailableDecision(operation))
                .toList();
        return new MerchantExecutionPolicy(values);
    }

    private static CommerceCapabilityDecision unavailableDecision(CommerceOperation operation) {
        return new CommerceCapabilityDecision(
                operation,
                false,
                CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.UNSUPPORTED),
                false,
                CapabilityIntegrationHealth.NO_INTEGRATION,
                false,
                CapabilityAvailability.UNAVAILABLE,
                CommerceExecutionRail.NONE,
                List.of(
                        CapabilityIneligibilityReason.NOT_ADVERTISED,
                        CapabilityIneligibilityReason.OPERATION_UNSUPPORTED,
                        CapabilityIneligibilityReason.ROLLOUT_DISABLED,
                        CapabilityIneligibilityReason.INTEGRATION_UNHEALTHY,
                        CapabilityIneligibilityReason.NO_FALLBACK
                ),
                null,
                null
        );
    }
}

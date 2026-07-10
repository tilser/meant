package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CapabilityEvaluationInput;
import com.meant.api.module.merchant.service.dto.CapabilityFallback;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CapabilityExecutionPolicyEvaluatorTest {

    private final CapabilityExecutionPolicyEvaluator evaluator = new CapabilityExecutionPolicyEvaluator();

    @Test
    void selectsDirectRailOnlyWhenEveryIndependentGateIsReady() {
        var decision = evaluator.evaluate(input(
                true,
                CapabilityAuthorizationDecision.ready("TOKEN", "TOKEN", Set.of("complete_checkout")),
                true,
                CapabilityIntegrationHealth.HEALTHY,
                CapabilityFallback.supported(CommerceExecutionRail.MERCHANT_HANDOFF)
        ));

        assertThat(decision.availability()).isEqualTo(CapabilityAvailability.AVAILABLE);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION);
        assertThat(decision.ineligibilityReasons()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("ineligibleGates")
    void producesDeterministicFallbackReasons(
            boolean advertised,
            CapabilityAuthorizationDecision authorization,
            boolean rolloutEnabled,
            CapabilityIntegrationHealth health,
            CapabilityIneligibilityReason reason
    ) {
        var decision = evaluator.evaluate(input(
                advertised,
                authorization,
                rolloutEnabled,
                health,
                CapabilityFallback.supported(CommerceExecutionRail.MERCHANT_HANDOFF)
        ));

        assertThat(decision.availability()).isEqualTo(CapabilityAvailability.FALLBACK_AVAILABLE);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.MERCHANT_HANDOFF);
        assertThat(decision.ineligibilityReasons())
                .contains(reason, CapabilityIneligibilityReason.FALLBACK_SELECTED);
    }

    @Test
    void unsupportedOperationWithoutFallbackIsUnavailable() {
        var decision = evaluator.evaluate(input(
                false,
                CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.UNSUPPORTED),
                true,
                CapabilityIntegrationHealth.HEALTHY,
                CapabilityFallback.unsupported()
        ));

        assertThat(decision.availability()).isEqualTo(CapabilityAvailability.UNAVAILABLE);
        assertThat(decision.selectedRail()).isEqualTo(CommerceExecutionRail.NONE);
        assertThat(decision.ineligibilityReasons()).contains(
                CapabilityIneligibilityReason.NOT_ADVERTISED,
                CapabilityIneligibilityReason.OPERATION_UNSUPPORTED,
                CapabilityIneligibilityReason.NO_FALLBACK
        );
    }

    private CapabilityEvaluationInput input(
            boolean advertised,
            CapabilityAuthorizationDecision authorization,
            boolean rolloutEnabled,
            CapabilityIntegrationHealth health,
            CapabilityFallback fallback
    ) {
        return new CapabilityEvaluationInput(
                CommerceOperation.DIRECT_CHECKOUT_COMPLETION,
                advertised,
                authorization,
                rolloutEnabled,
                health,
                fallback,
                CommerceExecutionRail.DIRECT_CHECKOUT_COMPLETION,
                UUID.randomUUID(),
                MerchantIntegrationProvider.SHOPIFY
        );
    }

    private static Stream<Arguments> ineligibleGates() {
        return Stream.of(
                Arguments.of(
                        false,
                        CapabilityAuthorizationDecision.notRequired(),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.NOT_ADVERTISED
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.unavailable(CapabilityAuthorizationStatus.NOT_AUTHORIZED),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.AUTHORIZATION_REQUIRED
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.missingScopes(
                                "TOKEN",
                                "TOKEN",
                                Set.of("complete_checkout"),
                                Set.of("complete_checkout")
                        ),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.MISSING_SCOPES
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.unavailable(
                                CapabilityAuthorizationStatus.AUTHENTICATION_DISABLED
                        ),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.AUTHENTICATION_DISABLED
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.tierNotGranted("TOKEN", "STANDARD"),
                        true,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.TIER_NOT_GRANTED
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.notRequired(),
                        false,
                        CapabilityIntegrationHealth.HEALTHY,
                        CapabilityIneligibilityReason.ROLLOUT_DISABLED
                ),
                Arguments.of(
                        true,
                        CapabilityAuthorizationDecision.notRequired(),
                        true,
                        CapabilityIntegrationHealth.SUSPENDED,
                        CapabilityIneligibilityReason.INTEGRATION_UNHEALTHY
                )
        );
    }
}

package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityAuthorizationStatus;
import com.meant.api.module.merchant.constant.CapabilityIneligibilityReason;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Status-aware UCP checkout session for an in-page checkout flow.")
public record CheckoutResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Meant cart identifier.")
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Merchant/provider cart identifier.")
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Merchant/provider checkout identifier.")
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Current provider checkout status.")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Provider checkout URL when available.")
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Provider continuation URL when available.")
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Negotiated UCP protocol version.")
        String ucpVersion,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Checkout total in minor currency units.")
        Long totalAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "ISO 4217 checkout currency code.")
        String currency,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the provider checkout state requires merchant or buyer escalation."
        )
        boolean requiresEscalation,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Authoritative next checkout action derived from session state and execution policy."
        )
        CheckoutNextAction nextAction,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Execution rail selected by the effective checkout policy."
        )
        CommerceExecutionRail selectedRail,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Typed reasons that made a preferred checkout rail ineligible or selected a fallback."
        )
        List<CapabilityIneligibilityReason> ineligibilityReasons,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Independent effective capability decisions for the merchant's commerce operations."
        )
        List<CapabilityDecisionResponse> capabilities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Provider messages for checkout guidance.")
        List<MessageResponse> messages,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                deprecated = true,
                description = "Deprecated compatibility view derived only from direct checkout completion availability."
        )
        boolean nativeCheckoutEnabled
) {

    public static CheckoutResponse from(CheckoutResult result) {
        return new CheckoutResponse(
                result.cartId(),
                result.remoteCartId(),
                result.checkoutId(),
                result.status(),
                result.checkoutUrl(),
                result.continueUrl(),
                result.ucpVersion(),
                result.totalAmountMinor(),
                result.currency(),
                result.requiresEscalation(),
                result.nextAction(),
                result.selectedRail(),
                result.ineligibilityReasons(),
                result.executionPolicy().decisions().stream()
                        .map(CapabilityDecisionResponse::from)
                        .toList(),
                result.messages().stream()
                        .map(MessageResponse::from)
                        .toList(),
                result.executionPolicy().isAvailable(CommerceOperation.DIRECT_CHECKOUT_COMPLETION)
        );
    }

    @Schema(description = "Effective availability of one provider-neutral commerce operation.")
    public record CapabilityDecisionResponse(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Commerce operation being evaluated.")
            CommerceOperation operation,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether the selected merchant/provider integration advertises the operation."
            )
            boolean advertised,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Stable authorization, tier, and granted-scope readiness outcome."
            )
            CapabilityAuthorizationStatus authorizationStatus,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether product rollout enables this operation for the merchant."
            )
            boolean rolloutEnabled,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Current health of the integration selected for this operation."
            )
            CapabilityIntegrationHealth integrationHealth,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether a supported fallback rail exists when the operation is ineligible."
            )
            boolean fallbackSupported,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Effective operation availability.")
            CapabilityAvailability availability,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Rail selected for this operation.")
            CommerceExecutionRail selectedRail,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Typed reasons explaining ineligibility or fallback selection."
            )
            List<CapabilityIneligibilityReason> ineligibilityReasons
    ) {

        private static CapabilityDecisionResponse from(CommerceCapabilityDecision decision) {
            return new CapabilityDecisionResponse(
                    decision.operation(),
                    decision.advertised(),
                    decision.authorization().status(),
                    decision.rolloutEnabled(),
                    decision.integrationHealth(),
                    decision.fallbackSupported(),
                    decision.availability(),
                    decision.selectedRail(),
                    decision.ineligibilityReasons()
            );
        }
    }

    @Schema(description = "UCP checkout message to present in the in-page checkout UI.")
    public record MessageResponse(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Provider message type.")
            String type,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Stable provider message code.")
            String code,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Provider message severity.")
            String severity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Buyer-facing provider message content.")
            String content,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Payload path related to the message.")
            String path
    ) {

        private static MessageResponse from(CheckoutResult.Message message) {
            return new MessageResponse(
                    message.type(),
                    message.code(),
                    message.severity(),
                    message.content(),
                    message.path()
            );
        }
    }
}

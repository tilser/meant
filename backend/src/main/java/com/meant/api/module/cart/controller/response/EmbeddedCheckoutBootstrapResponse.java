package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.constant.EmbeddedCheckoutBootstrapAction;
import com.meant.api.module.cart.service.dto.EmbeddedCheckoutBootstrapResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Short-lived, user-bound instructions for opening or falling back from embedded checkout.")
public record EmbeddedCheckoutBootstrapResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        EmbeddedCheckoutBootstrapAction action,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String fallbackContinueUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String protocolVersion,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String ecAuth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> allowedDelegations,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String merchantProvider,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String merchantDomain,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reason
) {
    public static EmbeddedCheckoutBootstrapResponse from(EmbeddedCheckoutBootstrapResult result) {
        return new EmbeddedCheckoutBootstrapResponse(
                result.action(), result.sessionId(), result.cartId(), result.checkoutId(), result.checkoutUrl(),
                result.fallbackContinueUrl(), result.protocolVersion(), result.ecAuth(), result.allowedDelegations(),
                result.expiresAt(), result.merchantProvider(), result.merchantDomain(), result.reason());
    }
}

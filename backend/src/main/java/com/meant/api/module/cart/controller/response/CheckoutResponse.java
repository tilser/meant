package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Status-aware UCP checkout session for an in-page checkout flow.")
public record CheckoutResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID cartId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String remoteCartId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String checkoutId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String status,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String checkoutUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String continueUrl,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String ucpVersion,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long totalAmountMinor,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean requiresEscalation,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Next checkout action derived from UCP status and message severity."
        )
        CheckoutNextAction nextAction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<MessageResponse> messages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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
                result.messages().stream()
                        .map(MessageResponse::from)
                        .toList(),
                result.nativeCheckoutEnabled()
        );
    }

    @Schema(description = "UCP checkout message to present in the in-page checkout UI.")
    public record MessageResponse(
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String type,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String code,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String severity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String content,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
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

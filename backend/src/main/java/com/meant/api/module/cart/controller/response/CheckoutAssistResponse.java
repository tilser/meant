package com.meant.api.module.cart.controller.response;

import com.meant.api.module.cart.service.dto.CheckoutAssistResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Checkout assistant reply, with the checkout session it acted on.")
public record CheckoutAssistResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String reply,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True when the assistant applied collected details to the merchant checkout."
        )
        boolean checkoutUpdated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CheckoutResponse checkout
) {

    public static CheckoutAssistResponse from(CheckoutAssistResult result) {
        return new CheckoutAssistResponse(
                result.reply(),
                result.checkoutUpdated(),
                CheckoutResponse.from(result.checkout())
        );
    }
}

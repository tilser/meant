package com.meant.api.module.cart.controller.response;

import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Saved buyer contact details that can be reused during checkout.")
public record SavedCheckoutBuyerResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Checkout contact email address.")
        String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Recipient first name.")
        String firstName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Recipient last name.")
        String lastName,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                nullable = true,
                description = "Checkout contact phone number when supplied."
        )
        String phoneNumber
) {

    public static SavedCheckoutBuyerResponse from(UserCheckoutDetailsResult details) {
        return new SavedCheckoutBuyerResponse(
                details.email(),
                details.firstName(),
                details.lastName(),
                details.phoneNumber()
        );
    }
}

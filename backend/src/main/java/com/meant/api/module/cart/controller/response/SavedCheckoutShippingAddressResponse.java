package com.meant.api.module.cart.controller.response;

import com.meant.api.module.user.service.dto.UserCheckoutDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Saved delivery address that can be reused during checkout.")
public record SavedCheckoutShippingAddressResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Primary street address line.")
        String streetAddress,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                nullable = true,
                description = "Apartment, suite, or secondary address line."
        )
        String extendedAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "City or locality.")
        String addressLocality,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                nullable = true,
                description = "State, province, or region."
        )
        String addressRegion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Postal or ZIP code.")
        String postalCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Delivery country code.")
        String addressCountry
) {

    public static SavedCheckoutShippingAddressResponse from(UserCheckoutDetailsResult details) {
        return new SavedCheckoutShippingAddressResponse(
                details.streetAddress(),
                details.extendedAddress(),
                details.addressLocality(),
                details.addressRegion(),
                details.postalCode(),
                details.addressCountry()
        );
    }
}

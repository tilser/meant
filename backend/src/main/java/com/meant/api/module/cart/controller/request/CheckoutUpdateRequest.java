package com.meant.api.module.cart.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Buyer-provided fields used to update a native UCP checkout session.")
public record CheckoutUpdateRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid
        @NotNull
        BuyerRequest buyer,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Valid
        @NotNull
        PostalAddressRequest shippingAddress,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        List<@NotBlank String> discountCodes
) {

    @Schema(description = "Buyer identity for checkout calculation and confirmation.")
    public record BuyerRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Email
            String email,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String firstName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String lastName,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String phoneNumber
    ) {
    }

    @Schema(description = "UCP postal address for physical fulfillment.")
    public record PostalAddressRequest(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String streetAddress,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String extendedAddress,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String addressLocality,
            @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String addressRegion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String postalCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String addressCountry
    ) {
    }
}

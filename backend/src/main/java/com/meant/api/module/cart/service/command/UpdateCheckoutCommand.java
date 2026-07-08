package com.meant.api.module.cart.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateCheckoutCommand(
        @NotNull
        UUID cartId,
        @NotNull
        UUID userId,
        @NotNull
        @Valid
        Buyer buyer,
        @NotNull
        @Valid
        PostalAddress shippingAddress,
        List<@NotBlank String> discountCodes
) {

    public record Buyer(
            @NotBlank
            @Email
            String email,
            @NotBlank
            String firstName,
            @NotBlank
            String lastName,
            String phoneNumber
    ) {
    }

    public record PostalAddress(
            @NotBlank
            String streetAddress,
            String extendedAddress,
            @NotBlank
            String addressLocality,
            String addressRegion,
            @NotBlank
            String postalCode,
            @NotBlank
            String addressCountry
    ) {
    }
}

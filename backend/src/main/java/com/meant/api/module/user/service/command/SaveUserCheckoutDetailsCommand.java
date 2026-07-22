package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SaveUserCheckoutDetailsCommand(
        @NotNull
        UUID userId,
        @NotBlank
        @Email
        String email,
        @NotBlank
        String firstName,
        @NotBlank
        String lastName,
        String phoneNumber,
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

package com.meant.api.module.agent.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AgentCheckoutToolArguments {

    private AgentCheckoutToolArguments() {
    }

    public record Prepare(
            @NotEmpty @Size(max = 10) List<@NotNull UUID> cartIds
    ) {
    }

    public record Get(
            @NotNull UUID cartId,
            Boolean refresh
    ) {
    }

    public record Update(
            @NotNull UUID cartId,
            @NotNull @Valid Buyer buyer,
            @NotNull @Valid PostalAddress shippingAddress,
            @Size(max = 20) List<@NotBlank @Size(max = 100) String> discountCodes
    ) {
    }

    public record Buyer(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @Size(max = 50) String phoneNumber
    ) {
    }

    public record PostalAddress(
            @NotBlank @Size(max = 200) String streetAddress,
            @Size(max = 200) String extendedAddress,
            @NotBlank @Size(max = 120) String addressLocality,
            @Size(max = 120) String addressRegion,
            @NotBlank @Size(max = 30) String postalCode,
            @NotBlank @Size(min = 2, max = 2) String addressCountry
    ) {
    }
}

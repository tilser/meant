package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional buyer identity used for merchant cart estimates.")
public record CartBuyerIdentityRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "buyer@example.com")
        String email,
        @JsonAlias({"phone", "phone_number"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "+14155552671")
        String phoneNumber,
        @JsonAlias("first_name")
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "Ada")
        String firstName,
        @JsonAlias("last_name")
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "Lovelace")
        String lastName,
        @JsonAlias({"country", "country_code"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "US")
        String countryCode
) {
}

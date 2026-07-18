package com.meant.api.module.cart.controller.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Postal address used for physical cart fulfillment.")
public record CartDeliveryAddressRequest(
        @JsonAlias("first_name")
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String firstName,
        @JsonAlias("last_name")
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String lastName,
        @JsonAlias({"phone", "phone_number"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String phoneNumber,
        @JsonAlias({"address1", "street_address"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String streetAddress,
        @JsonAlias({"address2", "extended_address"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String extendedAddress,
        @JsonAlias({"city", "address_locality"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String addressLocality,
        @JsonAlias({"province", "provinceCode", "province_code", "address_region"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String addressRegion,
        @JsonAlias({"zip", "postal_code"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String postalCode,
        @JsonAlias({"country", "countryCode", "country_code", "address_country"})
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String addressCountry
) {
}

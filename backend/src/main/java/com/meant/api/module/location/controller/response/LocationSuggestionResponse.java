package com.meant.api.module.location.controller.response;

import com.meant.api.module.location.service.dto.LocationSuggestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A provider-validated city and its UCP shipping-location values")
public record LocationSuggestionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "geonames:3067696")
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Prague")
        String city,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "Prague")
        String regionName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Czechia")
        String countryName,
        @Schema(
                description = "ISO 3166-1 alpha-2 country code used as UCP ships_to.country",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "CZ"
        )
        String country,
        @Schema(
                description = "ISO 3166-2 subdivision suffix used as UCP ships_to.region when available",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                example = "10"
        )
        String region,
        @Schema(
                description = "UCP ships_to.postal_code; absent for city-only suggestions",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String postalCode
) {

    public static LocationSuggestionResponse from(LocationSuggestion suggestion) {
        return new LocationSuggestionResponse(
                suggestion.id(),
                suggestion.city(),
                suggestion.regionName(),
                suggestion.countryName(),
                suggestion.country(),
                suggestion.region(),
                suggestion.postalCode()
        );
    }
}

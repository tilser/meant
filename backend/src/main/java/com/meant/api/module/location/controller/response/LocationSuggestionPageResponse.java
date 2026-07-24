package com.meant.api.module.location.controller.response;

import com.meant.api.module.location.service.dto.LocationSuggestionPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Worldwide city autocomplete results and required data attribution")
public record LocationSuggestionPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<LocationSuggestionResponse> suggestions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GeoNames")
        String attribution,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "https://www.geonames.org/")
        String attributionUrl
) {

    public static LocationSuggestionPageResponse from(LocationSuggestionPage page) {
        return new LocationSuggestionPageResponse(
                page.suggestions().stream().map(LocationSuggestionResponse::from).toList(),
                page.attribution(),
                page.attributionUrl()
        );
    }
}

package com.meant.api.module.location.controller;

import com.meant.api.module.location.controller.response.LocationSuggestionPageResponse;
import com.meant.api.module.location.service.LocationService;
import com.meant.api.module.location.service.query.SearchLocationsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Locations", description = "Validated worldwide delivery-location lookup")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/suggestions")
    @Operation(
            summary = "Suggest worldwide delivery cities",
            description = "Returns populated places with normalized country and optional region values suitable "
                    + "for the UCP ships_to filter."
    )
    @ApiResponse(responseCode = "200", description = "Matching location suggestions")
    public LocationSuggestionPageResponse suggestions(
            @Parameter(required = true, example = "Pra")
            @RequestParam
            @NotBlank
            @Size(min = 2, max = 80)
            String query,
            @Parameter(example = "8")
            @RequestParam(defaultValue = "8")
            @Min(1)
            @Max(10)
            int limit,
            @Parameter(example = "en")
            @RequestParam(defaultValue = "en")
            @Pattern(regexp = "[A-Za-z]{2,3}(?:-[A-Za-z]{2,8})?")
            String language
    ) {
        return LocationSuggestionPageResponse.from(locationService.search(
                new SearchLocationsQuery(query, limit, language)));
    }
}

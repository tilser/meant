package com.meant.api.module.location.service;

import com.meant.api.module.location.exception.InvalidLocationException;
import com.meant.api.module.location.service.dto.LocationSuggestion;
import com.meant.api.module.location.service.dto.LocationSuggestionPage;
import com.meant.api.module.location.service.port.LocationSearchProvider;
import com.meant.api.module.location.service.query.ResolveLocationQuery;
import com.meant.api.module.location.service.query.SearchLocationsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class LocationService {

    private static final String ATTRIBUTION = "GeoNames";
    private static final String ATTRIBUTION_URL = "https://www.geonames.org/";

    private final LocationSearchProvider provider;

    public LocationSuggestionPage search(@NotNull @Valid SearchLocationsQuery query) {
        return new LocationSuggestionPage(
                provider.search(query.query().trim(), query.limit(), query.language()),
                ATTRIBUTION,
                ATTRIBUTION_URL
        );
    }

    public LocationSuggestion resolve(@NotNull @Valid ResolveLocationQuery query) {
        LocationSuggestion location = provider.resolve(query.id(), query.language());
        if (location == null) {
            throw new InvalidLocationException("Location was not found: " + query.id());
        }
        return location;
    }
}

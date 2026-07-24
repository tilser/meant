package com.meant.api.module.location.service.dto;

import java.util.List;

public record LocationSuggestionPage(
        List<LocationSuggestion> suggestions,
        String attribution,
        String attributionUrl
) {

    public LocationSuggestionPage {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }
}

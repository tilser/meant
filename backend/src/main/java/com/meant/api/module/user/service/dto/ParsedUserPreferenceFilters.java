package com.meant.api.module.user.service.dto;

import java.util.List;

public record ParsedUserPreferenceFilters(
        List<String> filterIds,
        List<String> unmappedPreferences
) {

    public static ParsedUserPreferenceFilters empty() {
        return new ParsedUserPreferenceFilters(List.of(), List.of());
    }
}

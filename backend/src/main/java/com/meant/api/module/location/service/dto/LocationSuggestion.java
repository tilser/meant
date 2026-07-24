package com.meant.api.module.location.service.dto;

public record LocationSuggestion(
        String id,
        String city,
        String regionName,
        String countryName,
        String country,
        String region,
        String postalCode
) {
}

package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.UserSettings;
import com.meant.api.module.user.entity.UserSettingsLocation;

public record UserLocationResult(
        String id,
        String country,
        String code,
        String region,
        String postalCode,
        String regionName,
        String city
) {

    public UserLocationResult(String country, String code, String city) {
        this(
                com.meant.api.module.user.service.command.UserLocationCommand.legacyId(code, city),
                country,
                code,
                null,
                null,
                null,
                city
        );
    }

    public static UserLocationResult from(UserSettings settings) {
        if (settings.getLocationCountry() == null
                || settings.getLocationCode() == null
                || settings.getLocationCity() == null) {
            return null;
        }
        return new UserLocationResult(
                settings.getLocationId() == null
                        ? com.meant.api.module.user.service.command.UserLocationCommand.legacyId(
                                settings.getLocationCode(), settings.getLocationCity())
                        : settings.getLocationId(),
                settings.getLocationCountry(),
                settings.getLocationCode(),
                settings.getLocationRegion(),
                settings.getLocationPostalCode(),
                settings.getLocationRegionName(),
                settings.getLocationCity()
        );
    }

    public static UserLocationResult from(UserSettingsLocation location) {
        return new UserLocationResult(
                location.getId().getLocationId(),
                location.getCountry(),
                location.getLocationCode(),
                location.getLocationRegion(),
                location.getLocationPostalCode(),
                location.getLocationRegionName(),
                location.getLocationCity()
        );
    }
}

package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.entity.UserSettings;
import com.meant.api.module.user.entity.UserSettingsLocation;

public record UserLocationResult(
        String country,
        String code,
        String city
) {

    public static UserLocationResult from(UserSettings settings) {
        if (settings.getLocationCountry() == null
                || settings.getLocationCode() == null
                || settings.getLocationCity() == null) {
            return null;
        }
        return new UserLocationResult(
                settings.getLocationCountry(),
                settings.getLocationCode(),
                settings.getLocationCity()
        );
    }

    public static UserLocationResult from(UserSettingsLocation location) {
        return new UserLocationResult(
                location.getCountry(),
                location.getId().getLocationCode(),
                location.getId().getLocationCity()
        );
    }
}

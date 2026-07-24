package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record UserLocationCommand(
        @NotBlank
        @Size(max = 240)
        @Pattern(regexp = "(?:geonames:[1-9][0-9]{0,18}|legacy:[A-Za-z]{2}:[^\\r\\n]{1,200})")
        String id,

        @NotBlank
        @Size(max = 200)
        String country,

        @NotBlank
        @Pattern(regexp = "(?i)[A-Z]{2}")
        String code,

        @Size(max = 80)
        String region,

        @Size(max = 32)
        String postalCode,

        @Size(max = 200)
        String regionName,

        @NotBlank
        @Size(max = 200)
        String city
) {

    public UserLocationCommand(String country, String code, String city) {
        this(
                legacyId(code, city),
                country,
                code,
                null,
                null,
                null,
                city
        );
    }

    public static String legacyId(String code, String city) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String normalizedCity = city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
        return "legacy:" + normalizedCode + ":" + normalizedCity;
    }
}

package com.meant.api.module.location.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResolveLocationQuery(
        @NotBlank
        @Pattern(regexp = "geonames:[1-9][0-9]{0,18}")
        String id,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2,3}(?:-[A-Za-z]{2,8})?")
        String language
) {
}

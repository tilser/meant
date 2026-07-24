package com.meant.api.module.location.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchLocationsQuery(
        @NotBlank
        @Size(min = 2, max = 80)
        String query,

        @Min(1)
        @Max(10)
        int limit,

        @NotBlank
        @Pattern(regexp = "[A-Za-z]{2,3}(?:-[A-Za-z]{2,8})?")
        String language
) {
}

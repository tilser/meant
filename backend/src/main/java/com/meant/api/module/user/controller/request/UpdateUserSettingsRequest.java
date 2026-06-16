package com.meant.api.module.user.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateUserSettingsRequest(
        @Min(0)
        Integer budget,

        @Valid
        UserLocationRequest location,

        Set<@NotBlank String> filterIds,

        @Size(max = 12000)
        String preferenceDescription
) {
}

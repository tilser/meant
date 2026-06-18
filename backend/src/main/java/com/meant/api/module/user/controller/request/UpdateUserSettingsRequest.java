package com.meant.api.module.user.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record UpdateUserSettingsRequest(
        @Min(0)
        Integer budget,

        Boolean budgetUnlimited,

        @Pattern(regexp = "men|women|other|none")
        String clothingFit,

        @Valid
        UserLocationRequest location,

        @Size(max = 8)
        List<@NotNull @Valid UserLocationRequest> locations,

        Set<@NotBlank String> filterIds,

        @Size(max = 12000)
        String preferenceDescription
) {
}

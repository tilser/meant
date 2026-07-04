package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record UpdateUserSettingsRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Min(0)
        Integer budget,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean budgetUnlimited,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Pattern(regexp = "men|women|other|none")
        String clothingFit,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Valid
        UserLocationRequest location,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 8)
        List<@NotNull @Valid UserLocationRequest> locations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Set<@NotBlank String> filterIds,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 12000)
        String preferenceDescription
) {
}

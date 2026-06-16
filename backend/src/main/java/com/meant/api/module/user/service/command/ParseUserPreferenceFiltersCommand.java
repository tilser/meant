package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ParseUserPreferenceFiltersCommand(
        UUID userId,

        @NotBlank
        @Size(max = 12000)
        String description
) {
}

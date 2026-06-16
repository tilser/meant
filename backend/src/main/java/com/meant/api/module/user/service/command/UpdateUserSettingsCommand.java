package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateUserSettingsCommand(
        @NotNull
        UUID id,

        @Min(0)
        Integer budget,

        @Valid
        UserLocationCommand location,

        Set<@NotBlank String> filterIds,

        @NotNull
        Set<@NotBlank String> parsedFilterIds,

        @NotNull
        List<@NotBlank String> unmappedPreferences
) {
}

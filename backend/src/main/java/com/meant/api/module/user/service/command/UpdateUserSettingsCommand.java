package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpdateUserSettingsCommand(
        @NotNull
        UUID id,

        @Min(0)
        Integer budget,

        boolean budgetUnlimited,

        @Pattern(regexp = "men|women|other|none")
        String clothingFit,

        @Pattern(regexp = "(?i)[A-Z]{3}")
        String currency,

        @Valid
        UserLocationCommand location,

        @Size(max = 8)
        List<@NotNull @Valid UserLocationCommand> locations,

        Set<@NotBlank String> filterIds,

        @NotNull
        Set<@NotBlank String> parsedFilterIds,

        @NotNull
        List<@NotBlank String> unmappedPreferences
) {

    public UpdateUserSettingsCommand(
            UUID id,
            Integer budget,
            boolean budgetUnlimited,
            String clothingFit,
            UserLocationCommand location,
            List<UserLocationCommand> locations,
            Set<String> filterIds,
            Set<String> parsedFilterIds,
            List<String> unmappedPreferences
    ) {
        this(
                id,
                budget,
                budgetUnlimited,
                clothingFit,
                null,
                location,
                locations,
                filterIds,
                parsedFilterIds,
                unmappedPreferences
        );
    }
}

package com.meant.api.module.user.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SaveUserProductSearchPreferencesCommand(
        @NotNull UUID userId,
        @NotNull @Size(max = 20) List<@NotNull @Valid UserProductSearchPreferenceCommand> preferences
) {
}

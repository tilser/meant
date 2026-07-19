package com.meant.api.module.agent.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record VisibleProductContextCommand(
        @NotNull UUID sourceMessageId,
        @Size(min = 1, max = 4) List<@NotBlank @Size(max = 200) String> orderedCanonicalProductKeys
) {

    public VisibleProductContextCommand {
        orderedCanonicalProductKeys = orderedCanonicalProductKeys == null
                ? List.of()
                : List.copyOf(orderedCanonicalProductKeys);
    }
}

package com.meant.api.module.agent.service.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ShelfContextCommand(
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid ShelfItemCommand> items
) {

    public ShelfContextCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

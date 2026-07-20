package com.meant.api.module.agent.service.command;

import com.meant.api.module.agent.constant.AgentShelfItemKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ShelfItemCommand(
        @NotNull AgentShelfItemKind kind,
        @Size(max = 200) String canonicalProductKey,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 2000) String text,
        @NotNull @Size(max = 6) List<@NotBlank @Size(max = 300) String> relatedProductNames
) {

    public ShelfItemCommand {
        relatedProductNames = relatedProductNames == null ? List.of() : List.copyOf(relatedProductNames);
    }
}

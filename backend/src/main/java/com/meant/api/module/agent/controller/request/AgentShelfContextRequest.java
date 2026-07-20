package com.meant.api.module.agent.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "The browser Shelf snapshot when the user submitted an agent turn.")
public record AgentShelfContextRequest(
        @Schema(
                description = "Shelf items in their displayed order.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Size(min = 1, max = 50)
        List<@NotNull @Valid AgentShelfItemRequest> items
) {

    public AgentShelfContextRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

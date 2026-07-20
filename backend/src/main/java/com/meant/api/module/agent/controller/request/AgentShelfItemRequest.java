package com.meant.api.module.agent.controller.request;

import com.meant.api.module.agent.constant.AgentShelfItemKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "One client-side Shelf item supplied as untrusted display context.")
public record AgentShelfItemRequest(
        @Schema(description = "Shelf item type.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        AgentShelfItemKind kind,

        @Schema(
                description = "Client-known canonical product key for a product Shelf item.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 200)
        String canonicalProductKey,

        @Schema(description = "Displayed Shelf title.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 300)
        String title,

        @Schema(description = "Optional displayed detail text.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2000)
        String text,

        @Schema(
                description = "Displayed product names associated with a shelved message.",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Size(max = 6)
        List<@NotBlank @Size(max = 300) String> relatedProductNames
) {

    public AgentShelfItemRequest {
        relatedProductNames = relatedProductNames == null ? List.of() : List.copyOf(relatedProductNames);
    }
}

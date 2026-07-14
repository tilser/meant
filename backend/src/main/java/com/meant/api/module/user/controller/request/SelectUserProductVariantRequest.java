package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Select one exact product variant from a server-issued live or saved offer anchor")
public record SelectUserProductVariantRequest(
        @Schema(
                description = "Server-issued live canonical or durable saved offer key used only as the trusted anchor",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 200)
        String anchorOfferKey,
        @Schema(
                description = "Current option choices requested by the shopper; may be partial while selecting",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        @Size(max = 20)
        List<@Valid SelectedOption> selectedOptions,
        @Schema(
                description = "Option changed most recently; its name is placed first in provider relaxation priority",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 200)
        String preferredOptionName
) {

    @Schema(name = "UserProductVariantSelectedOption")
    public record SelectedOption(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Size(max = 200)
            String name,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @Size(max = 500)
            String value
    ) {
    }
}

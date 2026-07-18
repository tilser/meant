package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Item-reference similarity search narrowed by the originating user query")
public record UserSimilarProductSearchRequest(
        @Schema(
                description = "Originating product-search query used to narrow the similarity search",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank
        @Size(max = 500)
        String query,

        @Schema(
                description = "Server-issued READY qualification that supplied the originating query filters",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        UUID qualificationId
) {

    public UserSimilarProductSearchRequest(String query) {
        this(query, null);
    }
}

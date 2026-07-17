package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "One user turn in product-search qualification before catalog discovery.")
public record UserProductSearchQualificationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID conversationId,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID qualificationId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String message,

        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId
) {
}

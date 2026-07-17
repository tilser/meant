package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserProductSearchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(max = 500)
        String query,
        @Schema(
                description = "Server-issued READY qualification whose immutable plan authorizes this search",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull
        UUID qualificationId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        UUID merchantId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @PositiveOrZero
        @Max(UserProductSearchPagination.MAX_OFFSET)
        Integer offset,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Positive
        @Max(UserProductSearchPagination.MAX_LIMIT)
        Integer limit
) {
}

package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserProductSearchRequest(
        @NotBlank
        @Size(max = 500)
        String query,

        UUID merchantId,

        @PositiveOrZero
        @Max(UserProductSearchPagination.MAX_OFFSET)
        Integer offset,

        @Positive
        @Max(UserProductSearchPagination.MAX_LIMIT)
        Integer limit
) {
}

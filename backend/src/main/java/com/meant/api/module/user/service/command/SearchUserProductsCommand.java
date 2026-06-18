package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SearchUserProductsCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 500)
        String query,

        UUID merchantId,

        @Size(max = 128)
        String buyerIp,

        @Size(max = 512)
        String userAgent,

        @NotNull
        @PositiveOrZero
        @Max(UserProductSearchPagination.MAX_OFFSET)
        Integer offset,

        @NotNull
        @Positive
        @Max(UserProductSearchPagination.MAX_LIMIT)
        Integer limit
) {

    public SearchUserProductsCommand {
        offset = offset == null ? UserProductSearchPagination.DEFAULT_OFFSET : offset;
        limit = limit == null ? UserProductSearchPagination.DEFAULT_LIMIT : limit;
    }

    public SearchUserProductsCommand(UUID userId, String query, UUID merchantId) {
        this(userId, query, merchantId, null, null, null, null);
    }

    public SearchUserProductsCommand(UUID userId, String query, UUID merchantId, String buyerIp, String userAgent) {
        this(userId, query, merchantId, buyerIp, userAgent, null, null);
    }
}

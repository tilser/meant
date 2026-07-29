package com.meant.api.module.user.service.command;

import com.meant.api.common.util.AcceptLanguageParser;
import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.constant.UserProductSearchQueryLimits;
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
        @Size(max = UserProductSearchQueryLimits.MAX_SEARCH_QUERY_LENGTH)
        String query,

        UUID merchantId,

        @Size(max = 128)
        String buyerIp,

        @Size(max = 512)
        String userAgent,

        @Size(max = AcceptLanguageParser.MAXIMUM_LANGUAGE_TAG_LENGTH)
        String language,

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
        language = AcceptLanguageParser.canonicalLanguageTag(language);
        offset = offset == null ? UserProductSearchPagination.DEFAULT_OFFSET : offset;
        limit = limit == null ? UserProductSearchPagination.DEFAULT_LIMIT : limit;
    }

    public SearchUserProductsCommand(UUID userId, String query, UUID merchantId) {
        this(userId, query, merchantId, null, null, null, null, null);
    }

    public SearchUserProductsCommand(UUID userId, String query, UUID merchantId, String buyerIp, String userAgent) {
        this(userId, query, merchantId, buyerIp, userAgent, null, null, null);
    }

    public SearchUserProductsCommand(
            UUID userId,
            String query,
            UUID merchantId,
            String buyerIp,
            String userAgent,
            Integer offset,
            Integer limit
    ) {
        this(userId, query, merchantId, buyerIp, userAgent, null, offset, limit);
    }
}

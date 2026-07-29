package com.meant.api.module.user.service.command;

import com.meant.api.common.util.AcceptLanguageParser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SearchSimilarUserProductsCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 200)
        String canonicalProductKey,

        @NotBlank
        @Size(max = 500)
        String query,

        @NotNull
        UUID qualificationId,

        UUID merchantId,

        @Size(max = 128)
        String buyerIp,

        @Size(max = 512)
        String userAgent,

        @Size(max = AcceptLanguageParser.MAXIMUM_LANGUAGE_TAG_LENGTH)
        String language
) {

    public SearchSimilarUserProductsCommand {
        canonicalProductKey = canonicalProductKey == null ? null : canonicalProductKey.trim();
        query = query == null ? null : query.trim();
        buyerIp = buyerIp == null || buyerIp.isBlank() ? null : buyerIp.trim();
        userAgent = userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
        language = AcceptLanguageParser.canonicalLanguageTag(language);
    }

    public SearchSimilarUserProductsCommand(
            UUID userId,
            String canonicalProductKey,
            String query,
            UUID qualificationId,
            String buyerIp,
            String userAgent
    ) {
        this(userId, canonicalProductKey, query, qualificationId, null, buyerIp, userAgent, null);
    }

    public SearchSimilarUserProductsCommand(
            UUID userId,
            String canonicalProductKey,
            String query,
            UUID qualificationId,
            UUID merchantId,
            String buyerIp,
            String userAgent
    ) {
        this(userId, canonicalProductKey, query, qualificationId, merchantId, buyerIp, userAgent, null);
    }

}

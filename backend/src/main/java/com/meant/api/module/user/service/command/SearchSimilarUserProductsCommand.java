package com.meant.api.module.user.service.command;

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

        UUID qualificationId,

        @Size(max = 128)
        String buyerIp,

        @Size(max = 512)
        String userAgent
) {

    public SearchSimilarUserProductsCommand {
        canonicalProductKey = canonicalProductKey == null ? null : canonicalProductKey.trim();
        query = query == null ? null : query.trim();
        buyerIp = buyerIp == null || buyerIp.isBlank() ? null : buyerIp.trim();
        userAgent = userAgent == null || userAgent.isBlank() ? null : userAgent.trim();
    }

    public SearchSimilarUserProductsCommand(
            UUID userId,
            String canonicalProductKey,
            String query,
            String buyerIp,
            String userAgent
    ) {
        this(userId, canonicalProductKey, query, null, buyerIp, userAgent);
    }
}

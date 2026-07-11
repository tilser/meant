package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ResolveUserSelectedOfferQuery(
        @NotNull UUID userId,
        @NotBlank @Size(max = 200) String offerKey,
        @Size(max = 2) String countryCode
) {
    public ResolveUserSelectedOfferQuery {
        offerKey = offerKey == null ? null : offerKey.trim();
    }

    public ResolveUserSelectedOfferQuery(UUID userId, String offerKey) {
        this(userId, offerKey, null);
    }
}

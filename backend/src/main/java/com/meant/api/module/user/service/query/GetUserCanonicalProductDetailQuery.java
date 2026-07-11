package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GetUserCanonicalProductDetailQuery(
        @NotNull UUID userId,
        @NotBlank @Size(max = 200) String canonicalProductKey,
        @Size(max = 200) String selectedOfferKey
) {
    public GetUserCanonicalProductDetailQuery {
        canonicalProductKey = canonicalProductKey == null ? null : canonicalProductKey.trim();
        selectedOfferKey = selectedOfferKey == null || selectedOfferKey.isBlank() ? null : selectedOfferKey.trim();
    }
}

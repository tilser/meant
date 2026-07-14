package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GetSavedProductQuery(
        @NotNull UUID userId,
        @NotBlank @Size(max = 500) String productKey
) {
    public GetSavedProductQuery {
        productKey = productKey == null ? null : productKey.trim();
    }
}

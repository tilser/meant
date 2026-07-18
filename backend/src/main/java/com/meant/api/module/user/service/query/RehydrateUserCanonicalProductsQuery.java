package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RehydrateUserCanonicalProductsQuery(
        @NotNull UUID userId,
        @NotEmpty @Size(max = 21)
        List<@NotBlank @Size(max = 200) String> canonicalProductKeys
) {
    public RehydrateUserCanonicalProductsQuery {
        canonicalProductKeys = canonicalProductKeys == null ? null : canonicalProductKeys.stream()
                .map(key -> key == null ? null : key.trim())
                .toList();
    }
}

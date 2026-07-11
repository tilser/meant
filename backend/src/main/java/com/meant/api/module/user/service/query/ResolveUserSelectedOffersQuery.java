package com.meant.api.module.user.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ResolveUserSelectedOffersQuery(
        @NotNull UUID userId,
        @NotEmpty List<@NotBlank @Size(max = 200) String> offerKeys,
        @Size(max = 2) String countryCode
) {
    public ResolveUserSelectedOffersQuery {
        offerKeys = offerKeys == null ? null : offerKeys.stream()
                .map(key -> key == null ? null : key.trim())
                .toList();
    }
}

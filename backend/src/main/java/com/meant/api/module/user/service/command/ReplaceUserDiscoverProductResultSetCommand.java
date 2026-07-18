package com.meant.api.module.user.service.command;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record ReplaceUserDiscoverProductResultSetCommand(
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        @NotNull UUID qualificationId,
        @PositiveOrZero int offset,
        @Positive int resultLimit,
        @Positive Integer nextOffset,
        boolean hasMore,
        boolean upstreamTruncated,
        @NotNull List<@NotNull @Valid CanonicalProduct> products
) {
    public ReplaceUserDiscoverProductResultSetCommand {
        if (hasMore != (nextOffset != null)) {
            throw new IllegalArgumentException("A next offset is required exactly when more results are available");
        }
        if (nextOffset != null && nextOffset <= offset) {
            throw new IllegalArgumentException("Next offset must be greater than the current offset");
        }
        products = products == null ? null : List.copyOf(products);
    }
}

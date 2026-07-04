package com.meant.api.module.order.service.query;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ListOrdersQuery(
        @NotNull
        UUID userId,

        @PositiveOrZero
        int page,

        @Positive
        int limit
) {
}

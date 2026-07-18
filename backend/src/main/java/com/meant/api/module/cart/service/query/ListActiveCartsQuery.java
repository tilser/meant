package com.meant.api.module.cart.service.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ListActiveCartsQuery(
        @NotNull UUID userId,
        @Min(1) @Max(20) int limit
) {
}

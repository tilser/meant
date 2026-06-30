package com.meant.api.module.order.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ListOrdersQuery(
        @NotNull
        UUID userId
) {
}

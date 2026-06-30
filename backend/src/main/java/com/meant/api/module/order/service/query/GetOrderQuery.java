package com.meant.api.module.order.service.query;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GetOrderQuery(
        @NotNull
        UUID orderId,
        @NotNull
        UUID userId,
        boolean refresh
) {
}

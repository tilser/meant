package com.meant.api.module.cart.service.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FindActiveCartByRoutingScopeQuery(
        @NotNull UUID userId,
        @NotBlank String routingScopeKey
) {
}

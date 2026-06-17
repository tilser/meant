package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SearchUserProductsCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 500)
        String query
) {
}

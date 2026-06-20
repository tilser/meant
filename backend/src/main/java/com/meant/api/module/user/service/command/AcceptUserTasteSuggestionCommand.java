package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AcceptUserTasteSuggestionCommand(
        @NotNull
        UUID userId,

        @NotBlank
        String filterId
) {
}

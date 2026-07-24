package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CancelUserProductSearchQualificationCommand(
        @NotNull UUID qualificationId,
        @NotNull UUID userId,
        @NotNull UUID conversationId,
        UUID merchantId,
        @NotNull Instant expectedUpdatedAt
) {
}

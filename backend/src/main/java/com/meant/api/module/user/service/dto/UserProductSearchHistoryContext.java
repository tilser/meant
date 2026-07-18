package com.meant.api.module.user.service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Server-resolved ownership context for making one search page reopenable from Discover history. */
public record UserProductSearchHistoryContext(
        @NotNull UUID conversationId,
        @NotNull UUID qualificationId
) {
}

package com.meant.api.module.review.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DiscoverReviewProvidersCommand(
        @NotNull
        @Positive
        Integer batchSize
) {
}

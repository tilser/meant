package com.meant.api.module.merchant.service.command;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnrichMerchantsCommand(
        @NotNull
        @Positive
        Integer batchSize
) {
}

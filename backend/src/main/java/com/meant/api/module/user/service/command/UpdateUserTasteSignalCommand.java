package com.meant.api.module.user.service.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateUserTasteSignalCommand(
        @NotNull
        UUID userId,

        @NotNull
        UUID signalId,

        @DecimalMin("-10.0")
        @DecimalMax("10.0")
        Double weight,

        Boolean disabled
) {
}

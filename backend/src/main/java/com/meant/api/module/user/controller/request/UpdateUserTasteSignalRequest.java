package com.meant.api.module.user.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record UpdateUserTasteSignalRequest(
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @DecimalMin("-10.0")
        @DecimalMax("10.0")
        Double weight,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Boolean disabled
) {
}

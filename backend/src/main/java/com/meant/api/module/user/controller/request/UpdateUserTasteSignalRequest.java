package com.meant.api.module.user.controller.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record UpdateUserTasteSignalRequest(
        @DecimalMin("-10.0")
        @DecimalMax("10.0")
        Double weight,

        Boolean disabled
) {
}

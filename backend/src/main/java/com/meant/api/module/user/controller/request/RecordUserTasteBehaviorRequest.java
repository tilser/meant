package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserTasteBehaviorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RecordUserTasteBehaviorRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UserTasteBehaviorType behavior,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Valid
        SaveUserProductRequest product
) {
}

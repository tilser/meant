package com.meant.api.module.user.controller.request;

import com.meant.api.module.user.constant.UserTasteBehaviorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RecordUserTasteBehaviorRequest(
        @NotNull
        UserTasteBehaviorType behavior,

        @NotNull
        @Valid
        SaveUserProductRequest product
) {
}

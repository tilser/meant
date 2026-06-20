package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserTasteBehaviorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordUserTasteBehaviorCommand(
        @NotNull
        UUID userId,

        @NotNull
        UserTasteBehaviorType behavior,

        @NotNull
        @Valid
        SaveUserProductCommand product
) {
}

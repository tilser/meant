package com.meant.api.module.user.service.command;

import com.meant.api.module.user.constant.UserProductSearchPagination;
import com.meant.api.module.user.service.dto.UserProductSearchProductSnapshot;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import com.meant.api.module.user.service.dto.UserTasteProfileResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurateUserProductSearchCommand(
        @NotNull
        UUID userId,

        @NotBlank
        @Size(max = 500)
        String query,

        @NotBlank
        String normalizedQuery,

        @NotBlank
        String profileHash,

        @NotNull
        @Valid
        UserSettingsResult settings,

        @NotNull
        @Valid
        UserTasteProfileResult tasteProfile,

        @NotNull
        List<@NotNull UserProductSearchProductSnapshot> products,

        @NotNull
        Instant now,

        @NotNull
        @PositiveOrZero
        @Max(UserProductSearchPagination.MAX_OFFSET)
        Integer offset,

        @NotNull
        @Positive
        @Max(UserProductSearchPagination.MAX_LIMIT)
        Integer limit
) {

    public CurateUserProductSearchCommand {
        products = products == null ? List.of() : List.copyOf(products);
    }
}

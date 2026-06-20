package com.meant.api.module.user.service.query;

import com.meant.api.module.user.constant.UserProductDiscoverySortDirection;
import com.meant.api.module.user.constant.UserProductDiscoverySortField;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record GetUserProductDiscoveryQuery(
        @NotNull
        UUID userId,

        @Size(max = 120)
        String search,

        @NotNull
        UserProductDiscoverySortField sortBy,

        @NotNull
        UserProductDiscoverySortDirection sortDirection
) {
}

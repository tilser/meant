package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserInventoryExportResult;
import java.time.Instant;
import java.util.List;

public record UserInventoryExportResponse(
        Instant exportedAt,
        List<UserInventoryItemResponse> items
) {

    public static UserInventoryExportResponse from(UserInventoryExportResult result) {
        return new UserInventoryExportResponse(
                result.exportedAt(),
                result.items().stream()
                        .map(UserInventoryItemResponse::from)
                        .toList()
        );
    }
}

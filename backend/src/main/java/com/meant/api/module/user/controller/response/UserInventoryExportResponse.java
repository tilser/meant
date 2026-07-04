package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserInventoryExportResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record UserInventoryExportResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant exportedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
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

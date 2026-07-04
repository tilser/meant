package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchStreamEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record UserProductSearchStreamEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String agent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String productKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UserProductSearchProductResponse product,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<UserProductSearchProductResponse> products,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String query,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String normalizedQuery,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String profileHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean cached,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer offset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer nextOffset,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean hasMore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {

    public static UserProductSearchStreamEventResponse from(UserProductSearchStreamEvent event) {
        return new UserProductSearchStreamEventResponse(
                event.type(),
                event.agent(),
                event.label(),
                event.productKey(),
                event.product() == null ? null : UserProductSearchProductResponse.from(event.product()),
                event.products().stream()
                        .map(UserProductSearchProductResponse::from)
                        .toList(),
                event.query(),
                event.normalizedQuery(),
                event.profileHash(),
                event.cached(),
                event.offset(),
                event.limit(),
                event.nextOffset(),
                event.hasMore(),
                event.message()
        );
    }

    public static UserProductSearchStreamEventResponse error(String message) {
        return from(UserProductSearchStreamEvent.error(message));
    }
}

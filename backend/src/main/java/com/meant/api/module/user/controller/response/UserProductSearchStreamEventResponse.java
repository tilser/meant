package com.meant.api.module.user.controller.response;

import com.meant.api.module.user.service.dto.UserProductSearchStreamEvent;
import java.util.List;

public record UserProductSearchStreamEventResponse(
        String type,
        String agent,
        String label,
        String productKey,
        UserProductSearchProductResponse product,
        List<UserProductSearchProductResponse> products,
        String query,
        String normalizedQuery,
        String profileHash,
        Boolean cached,
        Integer offset,
        Integer limit,
        Integer nextOffset,
        Boolean hasMore,
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

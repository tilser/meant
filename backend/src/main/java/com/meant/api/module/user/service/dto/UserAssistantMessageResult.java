package com.meant.api.module.user.service.dto;

import com.meant.api.module.user.constant.UserAssistantMessageRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAssistantMessageResult(
        UUID id,
        UserAssistantMessageRole role,
        String content,
        List<UserProductSearchProductResult> products,
        Instant createdAt
) {

    public UserAssistantMessageResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}

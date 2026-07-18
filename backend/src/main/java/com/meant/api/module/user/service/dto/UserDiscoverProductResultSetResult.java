package com.meant.api.module.user.service.dto;

import java.util.List;
import java.util.UUID;

public record UserDiscoverProductResultSetResult(
        UUID resultSetId,
        List<UserDiscoverProductResult> products,
        int unavailableCount
) {
    public UserDiscoverProductResultSetResult {
        products = products == null ? List.of() : List.copyOf(products);
    }
}

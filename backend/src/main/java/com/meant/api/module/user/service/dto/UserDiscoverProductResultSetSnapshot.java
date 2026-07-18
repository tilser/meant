package com.meant.api.module.user.service.dto;

import java.util.List;
import java.util.UUID;

/** Ordered durable identifiers needed to rehydrate one historical Discover result page. */
public record UserDiscoverProductResultSetSnapshot(
        UUID resultSetId,
        int resultCount,
        List<String> canonicalProductKeys
) {
    public UserDiscoverProductResultSetSnapshot {
        canonicalProductKeys = canonicalProductKeys == null ? List.of() : List.copyOf(canonicalProductKeys);
    }
}

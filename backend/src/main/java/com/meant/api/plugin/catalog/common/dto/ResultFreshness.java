package com.meant.api.plugin.catalog.common.dto;

import java.time.Instant;

/** Observation time and, when the source supplies one, the time after which it is stale. */
public record ResultFreshness(
        Instant observedAt,
        Instant freshUntil
) {

    public ResultFreshness {
        if (observedAt == null) {
            throw new IllegalArgumentException("Observed timestamp must not be null");
        }
        if (freshUntil != null && freshUntil.isBefore(observedAt)) {
            throw new IllegalArgumentException("Fresh-until timestamp must not precede observation");
        }
    }
}

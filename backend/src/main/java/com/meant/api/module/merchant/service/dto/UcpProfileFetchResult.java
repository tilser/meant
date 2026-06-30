package com.meant.api.module.merchant.service.dto;

import java.time.Instant;

public record UcpProfileFetchResult(
        UcpProfile profile,
        String rawProfile,
        String endpoint,
        Instant capturedAt
) {
}

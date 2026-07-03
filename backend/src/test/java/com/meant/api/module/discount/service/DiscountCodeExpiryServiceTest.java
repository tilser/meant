package com.meant.api.module.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.discount.constant.DiscountCodeStatus;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiscountCodeExpiryServiceTest {

    private final DiscountCodeExpiryService service = new DiscountCodeExpiryService(new DiscountCodeSearchProperties(
            Duration.ofHours(48),
            Duration.ofHours(12),
            Duration.ofHours(1),
            10,
            "test-model:online",
            8
    ));

    @Test
    void parsedValidUntilDrivesValidCandidateExpiry() {
        Instant now = Instant.parse("2026-07-03T10:00:00Z");
        Instant validUntil = service.validUntil("December 31, 2026", now);

        assertThat(validUntil).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(service.expiresAt(DiscountCodeStatus.VALID, validUntil, now)).isEqualTo(validUntil);
    }

    @Test
    void missingValidUntilUsesCandidateTtl() {
        Instant now = Instant.parse("2026-07-03T10:00:00Z");

        assertThat(service.validUntil("", now)).isNull();
        assertThat(service.expiresAt(DiscountCodeStatus.VALID, null, now))
                .isEqualTo(Instant.parse("2026-07-05T10:00:00Z"));
    }

    @Test
    void invalidCandidateUsesInvalidTtl() {
        Instant now = Instant.parse("2026-07-03T10:00:00Z");

        assertThat(service.expiresAt(DiscountCodeStatus.INVALID, null, now))
                .isEqualTo(Instant.parse("2026-07-03T22:00:00Z"));
    }
}

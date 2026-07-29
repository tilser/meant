package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import com.meant.api.module.user.properties.UserProductSearchProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UserProductSearchStreamTimeoutBudgetValidatorTest {

    @Test
    void acceptsAStreamTimeoutThatContainsFederationAndTerminalDelivery() {
        var validator = validator(Duration.ofSeconds(52), Duration.ofSeconds(51));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAStreamTimeoutWithoutTerminalEventDeliveryReserve() {
        var validator = validator(Duration.ofSeconds(51), Duration.ofSeconds(51));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user.product-search.stream-timeout")
                .hasMessageContaining("PT52S")
                .hasMessageContaining("terminal-event delivery reserve");
    }

    @Test
    void rejectsAStreamTimeoutThatCannotBeUsedForNanosecondScheduling() {
        var validator = validator(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofSeconds(51));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user.product-search.stream-timeout")
                .hasMessageContaining("nanosecond deadline capacity");
    }

    @Test
    void rejectsAComputedStreamBudgetThatOverflowsNanosecondScheduling() {
        var validator = validator(
                Duration.ofNanos(Long.MAX_VALUE),
                Duration.ofNanos(Long.MAX_VALUE)
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Computed product-search stream budget")
                .hasMessageContaining("nanosecond deadline capacity");
    }

    private UserProductSearchStreamTimeoutBudgetValidator validator(
            Duration streamTimeout,
            Duration federationDeadline
    ) {
        return new UserProductSearchStreamTimeoutBudgetValidator(
                properties(streamTimeout),
                new FederatedCatalogDiscoveryProperties(federationDeadline)
        );
    }

    private UserProductSearchProperties properties(Duration streamTimeout) {
        return new UserProductSearchProperties(
                "v4",
                "v4",
                "v3",
                4_096,
                Duration.ofHours(24),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                100,
                streamTimeout,
                128,
                10,
                50,
                Duration.ofDays(30),
                Duration.ofDays(365),
                10,
                2,
                80
        );
    }
}

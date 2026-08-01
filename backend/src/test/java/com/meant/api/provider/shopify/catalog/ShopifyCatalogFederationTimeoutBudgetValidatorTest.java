package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShopifyCatalogFederationTimeoutBudgetValidatorTest {

    @Test
    void acceptsAnOverallDeadlineEqualToTheLiveDiscoverySourceBudget() {
        var validator = validator(
                Duration.ofSeconds(71),
                catalogProperties(true, true)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOverallDeadlineThatTruncatesLiveDiscoveryAndCatalog() {
        var validator = validator(
                Duration.ofSeconds(70),
                catalogProperties(true, true)
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.catalog.federation.overall-deadline")
                .hasMessageContaining("PT1M11S");
    }

    @Test
    void acceptsAnOverallDeadlineEqualToThePinnedRouteSourceBudget() {
        var validator = validator(
                Duration.ofSeconds(61),
                catalogProperties(true, false)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOverallDeadlineThatTruncatesThePinnedCatalogCall() {
        var validator = validator(
                Duration.ofSeconds(60),
                catalogProperties(true, false)
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.catalog.federation.overall-deadline")
                .hasMessageContaining("PT1M1S");
    }

    @Test
    void skipsTheBudgetWhenTheShopifySourceIsDisabled() {
        var validator = validator(
                Duration.ofSeconds(1),
                catalogProperties(false, true)
        );

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnOverallDeadlineThatCannotBeScheduledInNanoseconds() {
        var validator = validator(
                Duration.ofDays(1_000_000),
                catalogProperties(true, true)
        );

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("commerce.catalog.federation.overall-deadline")
                .hasMessageContaining("nanosecond deadline capacity");
    }

    private ShopifyCatalogFederationTimeoutBudgetValidator validator(
            Duration overallDeadline,
            ShopifyGlobalCatalogProperties catalogProperties
    ) {
        return new ShopifyCatalogFederationTimeoutBudgetValidator(
                new FederatedCatalogDiscoveryProperties(overallDeadline),
                catalogProperties
        );
    }

    private ShopifyGlobalCatalogProperties catalogProperties(
            boolean discoveryEnabled,
            boolean runtimeDiscoveryEnabled
    ) {
        return new ShopifyGlobalCatalogProperties(
                discoveryEnabled,
                runtimeDiscoveryEnabled,
                URI.create("https://catalog.shopify.test/api/ucp/mcp"),
                Set.of("catalog.shopify.test"),
                "2026-04-08",
                Duration.ofMinutes(15),
                10,
                50,
                50,
                200,
                16,
                Duration.ofSeconds(2),
                Duration.ofSeconds(8),
                Duration.ofSeconds(10),
                3,
                Duration.ofSeconds(30)
        );
    }
}

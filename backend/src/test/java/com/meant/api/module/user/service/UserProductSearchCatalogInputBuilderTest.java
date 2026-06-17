package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.user.properties.UserProductSearchProperties;
import com.meant.api.module.user.service.dto.ShoppingFilterResult;
import com.meant.api.module.user.service.dto.UserLocationResult;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchQueryIntentResult;
import com.meant.api.module.user.service.dto.UserSettingsResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserProductSearchCatalogInputBuilderTest {

    private final UserProductSearchCatalogInputBuilder builder = new UserProductSearchCatalogInputBuilder(
            new UserProductSearchHashService(properties())
    );

    @Test
    void buildsMcpPriceFilterAndCleansSearchQuery() {
        UserProductSearchCatalogInput input = builder.build(
                "T-shirt under 100 USD",
                intent("t-shirt under 100 usd"),
                settings(new UserLocationResult("United States", "US", "New York"))
        );

        assertThat(input.searchQuery()).isEqualTo("t-shirt");
        assertThat(input.context().addressCountry()).isEqualTo("US");
        assertThat(input.context().language()).isEqualTo("en");
        assertThat(input.context().currency()).isEqualTo("USD");
        assertThat(input.context().intent())
                .contains("Original request: T-shirt under 100 USD")
                .contains("Hard price filter: at most 100 USD");
        assertThat(input.filters().price().max()).isEqualTo(10000L);
        assertThat(input.cacheKey()).contains("priceMax=10000");
    }

    @Test
    void buildsRangePriceFilter() {
        UserProductSearchCatalogInput input = builder.build(
                "linen shirt between 50 and 100 eur",
                intent("linen shirt between 50 and 100 eur"),
                settings(new UserLocationResult("France", "FR", "Paris"))
        );

        assertThat(input.searchQuery()).isEqualTo("linen shirt");
        assertThat(input.context().currency()).isEqualTo("EUR");
        assertThat(input.filters().price().min()).isEqualTo(5000L);
        assertThat(input.filters().price().max()).isEqualTo(10000L);
    }

    @Test
    void parsesGroupedEuropeanPriceAmounts() {
        UserProductSearchCatalogInput input = builder.build(
                "linen shirt under 1.234,56 eur",
                intent("linen shirt under 1.234,56 eur"),
                settings(new UserLocationResult("France", "FR", "Paris"))
        );

        assertThat(input.searchQuery()).isEqualTo("linen shirt");
        assertThat(input.context().currency()).isEqualTo("EUR");
        assertThat(input.filters().price().max()).isEqualTo(123456L);
    }

    @Test
    void sendsLocationSignalsAndBudgetAsHardPriceFilterWhenQueryHasNoPrice() {
        UserProductSearchCatalogInput input = builder.build(
                "throw pillow",
                intent("throw pillow"),
                settings(new UserLocationResult("Czechia", "CZ", "Prague")),
                "203.0.113.4",
                "Meant Test"
        );

        assertThat(input.searchQuery()).isEqualTo("throw pillow");
        assertThat(input.context().addressCountry()).isEqualTo("CZ");
        assertThat(input.context().currency()).isEqualTo("CZK");
        assertThat(input.context().intent())
                .contains("Hard budget price filter: at most 120 CZK")
                .contains("User location signal: Prague, Czechia (CZ)")
                .contains("Organic - Prefer organic materials.");
        assertThat(input.signals().buyerIp()).isEqualTo("203.0.113.4");
        assertThat(input.signals().userAgent()).isEqualTo("Meant Test");
        assertThat(input.filters().price().max()).isEqualTo(12000L);
        assertThat(input.cacheKey())
                .contains("country=CZ", "currency=CZK", "priceMax=12000")
                .doesNotContain("buyerIp", "userAgent", "203.0.113.4", "Meant Test");
    }

    private UserProductSearchQueryIntentResult intent(String searchQuery) {
        return new UserProductSearchQueryIntentResult(
                searchQuery,
                searchQuery,
                searchQuery,
                searchQuery,
                searchQuery,
                searchQuery,
                searchQuery,
                List.of(),
                List.of(),
                "high",
                "test"
        );
    }

    private UserSettingsResult settings(UserLocationResult location) {
        return new UserSettingsResult(
                120,
                location,
                List.of(new ShoppingFilterResult(
                        "organic",
                        "Organic",
                        "Prefer organic materials.",
                        "materials",
                        "prefer",
                        10
                )),
                List.of(),
                List.of(),
                List.of(),
                Instant.parse("2026-06-17T10:00:00Z"),
                Instant.parse("2026-06-17T10:00:00Z")
        );
    }

    private UserProductSearchProperties properties() {
        return new UserProductSearchProperties(
                "v2",
                "v2",
                "v1",
                Duration.ofHours(24),
                5,
                12,
                Duration.ofHours(24),
                Duration.ofDays(7),
                6,
                3,
                80
        );
    }
}

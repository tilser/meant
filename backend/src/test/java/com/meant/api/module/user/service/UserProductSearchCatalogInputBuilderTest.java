package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeFilter;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryAttributeName;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryLocation;
import com.meant.api.module.user.exception.UnsupportedProductSearchCurrencyException;
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
    void copiesSavedUcpRegionAndPostalCodeIntoCatalogContext() {
        UserProductSearchCatalogInput input = builder.build(
                "running shoes",
                intent("running shoes"),
                settings(new UserLocationResult(
                        "geonames:5128581",
                        "United States",
                        "US",
                        "NY",
                        "10001",
                        "New York",
                        "New York"
                ))
        );

        assertThat(input.context().addressCountry()).isEqualTo("US");
        assertThat(input.context().addressRegion()).isEqualTo("NY");
        assertThat(input.context().postalCode()).isEqualTo("10001");
    }

    @Test
    void buildsRangePriceFilter() {
        UserProductSearchCatalogInput input = builder.build(
                "linen shirt between 50 and 100 USD",
                intent("linen shirt between 50 and 100 usd"),
                settings(new UserLocationResult("France", "FR", "Paris"))
        );

        assertThat(input.searchQuery()).isEqualTo("linen shirt");
        assertThat(input.context().currency()).isEqualTo("USD");
        assertThat(input.filters().price().min()).isEqualTo(5000L);
        assertThat(input.filters().price().max()).isEqualTo(10000L);
    }

    @Test
    void parsesGroupedEuropeanPriceAmounts() {
        UserProductSearchCatalogInput input = builder.build(
                "linen shirt under 1.234,56 USD",
                intent("linen shirt under 1.234,56 usd"),
                settings(new UserLocationResult("France", "FR", "Paris"))
        );

        assertThat(input.searchQuery()).isEqualTo("linen shirt");
        assertThat(input.context().currency()).isEqualTo("USD");
        assertThat(input.filters().price().max()).isEqualTo(123456L);
    }

    @Test
    void rejectsExplicitNonUsdPrice() {
        assertThatThrownBy(() -> builder.build(
                        "linen shirt under 100 EUR",
                        intent("linen shirt under 100 eur"),
                        settings(new UserLocationResult("France", "FR", "Paris"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
    }

    @Test
    void rejectsExplicitCzechKorunaPrice() {
        assertThatThrownBy(() -> builder.build(
                        "linen shirt under 2 000 Kč",
                        intent("linen shirt under 2 000 Kč"),
                        settings(new UserLocationResult("Czechia", "CZ", "Prague"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
    }

    @Test
    void rejectsCanadianAndAustralianDollarPrices() {
        assertThatThrownBy(() -> builder.build(
                        "hiking boots under 100 Canadian dollars",
                        intent("hiking boots under 100 Canadian dollars"),
                        settings(new UserLocationResult("Canada", "CA", "Toronto"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);

        assertThatThrownBy(() -> builder.build(
                        "hiking boots under 100 Australian dollars",
                        intent("hiking boots under 100 Australian dollars"),
                        settings(new UserLocationResult("Australia", "AU", "Sydney"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
    }

    @Test
    void rejectsMixedCurrencyRange() {
        assertThatThrownBy(() -> builder.build(
                        "hiking boots between $50 and €100",
                        intent("hiking boots between $50 and €100"),
                        settings(new UserLocationResult("United States", "US", "New York"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
    }

    @Test
    void rejectsConflictingSuffixAndAdditionalIsoCurrencies() {
        assertThatThrownBy(() -> builder.build(
                        "hiking boots under $100 EUR",
                        intent("hiking boots under $100 EUR"),
                        settings(new UserLocationResult("United States", "US", "New York"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);

        for (String query : List.of(
                "hiking boots under 100 CHF",
                "hiking boots under 100 SEK",
                "hiking boots under 100 INR",
                "hiking boots under ₹100"
        )) {
            assertThatThrownBy(() -> builder.build(
                            query,
                            intent(query),
                            settings(new UserLocationResult("United States", "US", "New York"))))
                    .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
        }
    }

    @Test
    void rejectsNonUsdSymbolsAndCurrenciesInConversationalBudgetText() {
        for (String value : List.of(
                "under 100€",
                "my budget is €100",
                "my budget is 100 EUR",
                "I can spend CAD 150"
        )) {
            assertThatThrownBy(() -> builder.validateSupportedCurrency(value))
                    .as(value)
                    .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
        }

        assertThatThrownBy(() -> builder.build(
                        "linen shirt under 100€",
                        intent("linen shirt under 100€"),
                        settings(new UserLocationResult("France", "FR", "Paris"))))
                .isInstanceOf(UnsupportedProductSearchCurrencyException.class);
    }

    @Test
    void sendsLocationSignalsWithoutTreatingAnUnconfirmedAccountBudgetAsHard() {
        UserProductSearchCatalogInput input = builder.build(
                "throw pillow",
                intent("throw pillow"),
                settings(new UserLocationResult("Czechia", "CZ", "Prague"), "men"),
                "203.0.113.4",
                "Meant Test"
        );

        assertThat(input.searchQuery()).isEqualTo("throw pillow");
        assertThat(input.context().addressCountry()).isEqualTo("CZ");
        assertThat(input.context().currency()).isEqualTo("USD");
        assertThat(input.context().intent())
                .contains("User delivery location signals: Prague, Czechia (CZ)")
                .contains("Hard apparel audience filter: men's sizing")
                .contains("Organic - Prefer organic materials.");
        assertThat(input.signals().buyerIp()).isEqualTo("203.0.113.4");
        assertThat(input.signals().userAgent()).isEqualTo("Meant Test");
        assertThat(input.filters()).isNull();
        assertThat(input.cacheKey())
                .contains("country=CZ", "currency=USD", "priceMax=")
                .doesNotContain("buyerIp", "userAgent", "203.0.113.4", "Meant Test");
    }

    @Test
    void qualifiedSearchUsesTypedLocationAndKeepsNonHardProfilePreferencesInIntent() {
        CatalogDiscoveryFilters qualifiedFilters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                new CatalogDiscoveryLocation("US", "NY", "10001"),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CatalogDiscoveryAttributeFilter(
                        CatalogDiscoveryAttributeName.SIZE,
                        List.of("10")
                )),
                null,
                List.of()
        );

        UserProductSearchCatalogInput input = builder.build(
                "trail running shoes",
                intent("trail running shoes"),
                settings(new UserLocationResult("Czechia", "CZ", "Prague"), "men"),
                null,
                null,
                qualifiedFilters
        );

        assertThat(input.context().addressCountry()).isEqualTo("US");
        assertThat(input.context().addressRegion()).isEqualTo("NY");
        assertThat(input.context().postalCode()).isEqualTo("10001");
        assertThat(input.context().intent())
                .contains("Catalog query: trail running shoes")
                .contains("Organic - Prefer organic materials.")
                .doesNotContain(
                        "Prague",
                        "Czechia",
                        "men's sizing"
                );
        assertThat(input.discoveryFilters()).isSameAs(qualifiedFilters);
    }

    @Test
    void providerIntentDoesNotDiscloseUnrelatedFoodPreferencesForFootwear() {
        UserSettingsResult base = settings(new UserLocationResult("United States", "US", "New York"));
        UserSettingsResult settings = new UserSettingsResult(
                base.budget(),
                base.clothingFit(),
                base.location(),
                base.locations(),
                List.of(
                        new ShoppingFilterResult(
                                "halal", "Halal", "Require products labeled halal.", "food", "require", 1),
                        new ShoppingFilterResult(
                                "natural-materials", "Natural materials", "Prefer natural materials.",
                                "materials", "prefer", 2)
                ),
                base.availableFilters(),
                base.parsedFilterIds(),
                base.unmappedPreferences(),
                base.createdAt(),
                base.updatedAt()
        );

        UserProductSearchCatalogInput input = builder.build(
                "football boots",
                intent("football boots"),
                settings
        );

        assertThat(input.context().intent())
                .contains("Natural materials")
                .doesNotContain("Halal", "halal");
        assertThat(settings.filters()).extracting(ShoppingFilterResult::id)
                .containsExactly("halal", "natural-materials");
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
        return settings(location, null);
    }

    private UserSettingsResult settings(UserLocationResult location, String clothingFit) {
        List<UserLocationResult> locations = location == null ? List.of() : List.of(location);
        return new UserSettingsResult(
                120,
                clothingFit,
                location,
                locations,
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
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                100,
                Duration.ofSeconds(45),
                128,
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

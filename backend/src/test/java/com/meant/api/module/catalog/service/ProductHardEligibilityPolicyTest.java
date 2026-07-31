package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchFilters;
import com.meant.api.plugin.catalog.common.dto.CatalogSearchPriceFilter;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductHardEligibilityPolicyTest {
    private final ProductRankingService service = ProductRankingTestFactory.service();

    @Test
    void activePriceFilterFailsClosedForNoPriceWrongCurrencyAndMixedOutsidePlusUnknown() {
        Offer noPrice = offer("A", "m1", "none", "v", null);
        Offer eur = offer("B", "m2", "eur", "v", new Money(5_000, "EUR"));
        Offer outside = offer("C", "m3", "mixed", "known", new Money(20_000, "USD"));
        Offer unknown = offer("C", "m4", "mixed", "unknown", null);
        Offer valid = offer("D", "m5", "valid", "v", new Money(7_500, "USD"));
        List<CanonicalProduct> products = List.of(
                product("none", List.of(category("apparel")), noPrice),
                product("eur", List.of(category("apparel")), eur),
                product("mixed", List.of(category("apparel")), outside, unknown),
                product("valid", List.of(category("apparel")), valid));
        CatalogSearchFilters filters = new CatalogSearchFilters(
                List.of(), new CatalogSearchPriceFilter(5_000L, 10_000L));

        assertThat(service.rank(products, RankingTestFixtures.context(
                "shirt", "USD", "US", filters, List.of(), 20)).products())
                .extracting(CanonicalProduct::key).containsExactly("valid");
    }

    @Test
    void activePriceAndAvailabilityConstraintsMustBeSatisfiedByTheSameOffer() {
        CanonicalProduct splitAvailability = product("split-availability", List.of(category("apparel")),
                offer("A", "m1", "split-availability", "cheap", new Money(8_000, "USD"),
                        OfferAvailabilityStatus.OUT_OF_STOCK),
                offer("A", "m2", "split-availability", "available", new Money(20_000, "USD"),
                        OfferAvailabilityStatus.IN_STOCK));
        CanonicalProduct splitCurrency = product("split-currency", List.of(category("apparel")),
                offer("B", "m3", "split-currency", "eur", new Money(8_000, "EUR"),
                        OfferAvailabilityStatus.IN_STOCK),
                offer("B", "m4", "split-currency", "usd", new Money(20_000, "USD"),
                        OfferAvailabilityStatus.IN_STOCK));
        CanonicalProduct valid = product("valid", List.of(category("apparel")),
                offer("C", "m5", "valid", "v", new Money(8_000, "USD"),
                        OfferAvailabilityStatus.IN_STOCK));
        CatalogSearchFilters filters = new CatalogSearchFilters(
                List.of(), new CatalogSearchPriceFilter(null, 10_000L));

        assertThat(service.rank(List.of(valid, splitCurrency, splitAvailability), RankingTestFixtures.context(
                "shirt", "USD", "US", filters, List.of(), 20)).products())
                .extracting(CanonicalProduct::key).containsExactly("valid");
    }

    @Test
    void unknownAvailabilityRemainsExplicitlyEligibleWhenTheSameOfferMatchesPrice() {
        CanonicalProduct unknown = product("unknown", List.of(category("apparel")),
                offer("A", "m", "unknown", "v", new Money(8_000, "USD"),
                        OfferAvailabilityStatus.UNKNOWN));
        CatalogSearchFilters filters = new CatalogSearchFilters(
                List.of(), new CatalogSearchPriceFilter(null, 10_000L));

        assertThat(service.rank(List.of(unknown), RankingTestFixtures.context(
                "shirt", "USD", "US", filters, List.of(), 20)).products())
                .extracting(CanonicalProduct::key).containsExactly("unknown");
    }

    @Test
    void preferredCurrencyRemovesMismatchedPricedOffersWithoutAPriceFilter() {
        CanonicalProduct mixed = product("mixed", List.of(category("apparel")),
                offer("A", "m1", "mixed", "usd", new Money(8_000, "USD")),
                offer("A", "m2", "mixed", "eur", new Money(7_000, "EUR")));
        CanonicalProduct eurOnly = product("eur", List.of(category("apparel")),
                offer("B", "m3", "eur", "eur", new Money(7_000, "EUR")));

        List<CanonicalProduct> products = service.rank(
                List.of(mixed, eurOnly),
                RankingTestFixtures.context("shirt", "USD", "US", null, List.of(), 20)
        ).products();

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.key()).isEqualTo("mixed");
            assertThat(product.offers()).singleElement().satisfies(offer ->
                    assertThat(offer.price().currency()).isEqualTo("USD"));
        });
    }

    @Test
    void categoryConstraintFailsClosedWhenEvidenceIsAbsentAndDoesNotSubstringMatchMenToWomen() {
        CanonicalProduct absent = product("absent", List.of(), offer("A", "m1", "absent", "v", new Money(1_000, "USD")));
        CanonicalProduct women = product("women", List.of(category("womens")), offer("B", "m2", "women", "v", new Money(1_000, "USD")));
        CanonicalProduct men = product("men", List.of(category("men")), offer("C", "m3", "men", "v", new Money(1_000, "USD")));
        CatalogSearchFilters filters = new CatalogSearchFilters(List.of("men"), null);

        assertThat(service.rank(List.of(absent, women, men), RankingTestFixtures.context(
                "shirt", "USD", "US", filters, List.of(), 20)).products())
                .extracting(CanonicalProduct::key).containsExactly("men");
    }

    @Test
    void categoryHierarchyUsesExplicitSeparatorsOnly() {
        CanonicalProduct child = product("child", List.of(category("men > shirts")),
                offer("A", "m", "child", "v", new Money(1_000, "USD")));
        CatalogSearchFilters filters = new CatalogSearchFilters(List.of("men"), null);

        assertThat(service.rank(List.of(child), RankingTestFixtures.context(
                "shirt", "USD", "US", filters, List.of(), 20)).products())
                .extracting(CanonicalProduct::key).containsExactly("child");
    }

    @Test
    void genericMoneyRejectsNegativeItemAndDeliveryAmounts() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Money(-1, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new com.meant.api.module.catalog.service.dto.OfferDelivery(
                        com.meant.api.module.catalog.service.dto.DeliveryMethod.SHIPPING, "US", 1, 2,
                        new Money(-1, "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Offer offer(String provider, String merchant, String product, String variant, Money price) {
        return offer(provider, merchant, product, variant, price, OfferAvailabilityStatus.IN_STOCK);
    }

    private Offer offer(
            String provider, String merchant, String product, String variant, Money price,
            OfferAvailabilityStatus availability
    ) {
        return RankingTestFixtures.offer(provider, merchant, product, variant, price,
                availability, null, List.of());
    }

    private CanonicalProduct product(String key, List<ProductAttribute> attributes, Offer... offers) {
        return RankingTestFixtures.product(key, "shirt", key.toUpperCase(), key + "-source", 5_000, attributes, offers);
    }

    private ProductAttribute category(String value) {
        return new ProductAttribute("taxonomy", "category", value);
    }
}

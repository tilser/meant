package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.catalog.properties.CatalogProductObservationCacheProperties;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.port.CatalogProductDetailProvider;
import com.meant.api.module.catalog.service.support.CatalogProductObservationCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CatalogProductDetailServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void removesMismatchedDetailPricesWhileKeepingVariantAvailability() {
        RehydratedProductDetails details = detailsWithCurrency("EUR");

        var result = service(null, details).getDetails(
                reference(),
                new CatalogRehydrationContext("US", "en", "USD")
        );

        assertThat(result.details().priceRange()).isNull();
        assertThat(result.details().listPriceRange()).isNull();
        assertThat(result.details().selectedVariant().priceAmount()).isNull();
        assertThat(result.details().selectedVariant().priceCurrency()).isNull();
        assertThat(result.details().selectedVariant().listPriceAmount()).isNull();
        assertThat(result.details().selectedVariant().available()).isTrue();
        assertThat(result.details().variants().getFirst().available()).isTrue();
    }

    @Test
    void partialSelectionCountsEveryCompatibleVariantWithoutMarkingItExact() {
        ProductAttribute blue = option("Color", "Blue");
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(List.of(blue), List.of());

        var result = service(selection, List.of(blue), List.of(
                List.of(blue, option("Size", "M")),
                List.of(blue, option("Size", "L")),
                List.of(option("Color", "Red"), option("Size", "M"))
        )).getDetails(reference(), selection, null);

        assertThat(result.selection().requestedOptions()).containsExactly(blue);
        assertThat(result.selection().effectiveOptions()).containsExactly(blue);
        assertThat(result.selection().complete()).isFalse();
        assertThat(result.selection().relaxed()).isFalse();
        assertThat(result.selection().matchingVariantCount()).isEqualTo(2);
        assertThat(result.selection().uniqueCompleteExactMatch()).isFalse();
    }

    @Test
    void completeSelectionIsCartSafeOnlyWhenExactlyOneVariantMatches() {
        ProductAttribute blue = option("Color", "Blue");
        ProductAttribute medium = option("Size", "M");
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(
                List.of(blue, medium), List.of());

        var result = service(
                selection,
                List.of(blue, medium),
                List.of(
                        List.of(blue, medium),
                        List.of(blue, option("Size", "L"))),
                List.of(blue, medium)
        ).getDetails(reference(), selection, null);

        assertThat(result.selection().complete()).isTrue();
        assertThat(result.selection().relaxed()).isFalse();
        assertThat(result.selection().matchingVariantCount()).isEqualTo(1);
        assertThat(result.selection().uniqueCompleteExactMatch()).isTrue();
    }

    @Test
    void featuredSelectionCountsWhenTheVariantsListOmitsIt() {
        ProductAttribute blue = option("Color", "Blue");
        ProductAttribute medium = option("Size", "M");
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(
                List.of(blue, medium), List.of());

        var result = service(
                selection,
                List.of(blue, medium),
                List.of(),
                List.of(blue, medium)
        ).getDetails(reference(), selection, null);

        assertThat(result.selection().matchingVariantCount()).isEqualTo(1);
        assertThat(result.selection().uniqueCompleteExactMatch()).isTrue();
    }

    @Test
    void emptySelectionIsExactOnlyForAnAuthoritativeSingleVariantProduct() {
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(List.of(), List.of());

        var result = service(selection, detailsWithoutOptions(1))
                .getDetails(reference(), selection, null);

        assertThat(result.selection().complete()).isTrue();
        assertThat(result.selection().matchingVariantCount()).isEqualTo(1);
        assertThat(result.selection().uniqueCompleteExactMatch()).isTrue();
    }

    @Test
    void emptySelectionRejectsAPartialResponseThatReportsMultipleVariants() {
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(List.of(), List.of());

        var result = service(selection, detailsWithoutOptions(9))
                .getDetails(reference(), selection, null);

        assertThat(result.selection().complete()).isFalse();
        assertThat(result.selection().matchingVariantCount()).isEqualTo(1);
        assertThat(result.selection().uniqueCompleteExactMatch()).isFalse();
    }

    @Test
    void reusesEquivalentCurrentDetailsAndKeepsInteractionKeysRequestScoped() {
        AtomicInteger calls = new AtomicInteger();
        RehydratedProductDetails details = detailsWithCurrency("USD");
        CatalogProductDetailProvider provider = new CatalogProductDetailProvider() {
            @Override
            public boolean supportsDetails(DiscoverySourceIdentity source) {
                return reference().discoverySource().equals(source);
            }

            @Override
            public CatalogProductDetailResult getDetails(
                    CatalogProductReference requested,
                    CatalogRehydrationContext context
            ) {
                calls.incrementAndGet();
                return currentDetail(requested, details);
            }
        };
        CatalogProductObservationCache cache = new CatalogProductObservationCache(
                new CatalogProductObservationCacheProperties(Duration.ofMinutes(2), 100, 100),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        CatalogProductDetailService service = new CatalogProductDetailService(
                List.of(provider),
                new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()),
                cache
        );
        CatalogProductReference firstReference = reference();
        CatalogProductReference secondReference = withInteractionKey(firstReference, "another-offer");
        CatalogRehydrationContext context = new CatalogRehydrationContext("US", "en", "USD");

        service.getDetails(firstReference, context);
        CatalogProductDetailResult cached = service.getDetails(secondReference, context);
        service.getDetails(secondReference, new CatalogRehydrationContext("CA", "en", "USD"));
        CatalogRehydrationContext selectionContext = new CatalogRehydrationContext("GB", "en", "USD");
        service.getDetails(
                secondReference,
                new CatalogProductDetailSelection(List.of(option("Color", "Blue")), List.of("Color")),
                selectionContext
        );

        assertThat(calls).hasValue(3);
        assertThat(cached.rehydration().reference()).isEqualTo(secondReference);
        assertThat(cached.rehydration().resolvedReference().interactionKey())
                .isEqualTo(secondReference.interactionKey());
        assertThat(cache.findRehydration(secondReference, selectionContext)).isEmpty();
    }

    private CatalogProductDetailService service(
            CatalogProductDetailSelection selection,
            List<ProductAttribute> effective,
            List<List<ProductAttribute>> variants
    ) {
        return service(selection, effective, variants, null);
    }

    private CatalogProductDetailService service(
            CatalogProductDetailSelection selection,
            List<ProductAttribute> effective,
            List<List<ProductAttribute>> variants,
            List<ProductAttribute> featured
    ) {
        return service(selection, details(effective, variants, featured));
    }

    private CatalogProductDetailService service(
            CatalogProductDetailSelection selection,
            RehydratedProductDetails details
    ) {
        CatalogProductReference reference = reference();
        ResultFreshness freshness = new ResultFreshness(
                NOW,
                NOW.plus(Duration.ofMinutes(5)));
        CatalogProductRehydrationResult rehydration = CatalogProductRehydrationResult.fresh(
                reference,
                reference,
                new RehydratedCommercialFacts(
                        "Product",
                        "Merchant",
                        null,
                        OfferAvailability.unknown(),
                        reference.externalVariantReference(),
                        reference.selectedOptions(),
                        List.of(),
                        List.of(),
                        freshness,
                        CommercialFactsFreshness.fromSingleObservation(freshness)));
        CatalogProductDetailResult providerResult = CatalogProductDetailResult.from(rehydration, details);
        CatalogProductDetailProvider provider = new CatalogProductDetailProvider() {
            @Override
            public boolean supportsDetails(DiscoverySourceIdentity source) {
                return reference.discoverySource().equals(source);
            }

            @Override
            public CatalogProductDetailResult getDetails(
                    CatalogProductReference ignored,
                    CatalogRehydrationContext context
            ) {
                return providerResult;
            }

            @Override
            public CatalogProductDetailResult getDetails(
                    CatalogProductReference ignored,
                    CatalogProductDetailSelection ignoredSelection,
                    CatalogRehydrationContext context
            ) {
                return providerResult;
            }
        };
        return new CatalogProductDetailService(
                List.of(provider),
                new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
    }

    private RehydratedProductDetails details(
            List<ProductAttribute> effective,
            List<List<ProductAttribute>> variants,
            List<ProductAttribute> featured
    ) {
        return new RehydratedProductDetails(
                "product",
                null,
                "Product",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new RehydratedProductDetails.Option("Color", List.of("Blue", "Red")),
                        new RehydratedProductDetails.Option("Size", List.of("M", "L"))),
                effective.stream()
                        .map(option -> new RehydratedProductDetails.SelectedOption(option.name(), option.value()))
                        .toList(),
                variants.stream().map(this::variant).toList(),
                null,
                null,
                null,
                false,
                featured == null ? null : variant(featured),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Merchant");
    }

    private RehydratedProductDetails detailsWithoutOptions(int totalVariants) {
        RehydratedProductDetails.Variant variant = variant("variant", List.of());
        return new RehydratedProductDetails(
                "product",
                null,
                "Product",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(variant),
                totalVariants,
                null,
                null,
                false,
                variant,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Merchant");
    }

    private RehydratedProductDetails detailsWithCurrency(String currency) {
        RehydratedProductDetails.Variant variant = new RehydratedProductDetails.Variant(
                "variant",
                null,
                "Black",
                null,
                null,
                "32.00",
                currency,
                "40.00",
                currency,
                null,
                null,
                null,
                List.of(),
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        return new RehydratedProductDetails(
                "product",
                null,
                "Salthouse T-Shirt Black",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(variant),
                1,
                new RehydratedProductDetails.PriceRange("32.00", "32.00", currency),
                new RehydratedProductDetails.PriceRange("40.00", "40.00", currency),
                false,
                variant,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "Salthouse"
        );
    }

    private RehydratedProductDetails.Variant variant(List<ProductAttribute> options) {
        String id = options.stream()
                .sorted(java.util.Comparator.comparing(ProductAttribute::name))
                .map(option -> option.name() + "=" + option.value())
                .collect(java.util.stream.Collectors.joining("|"));
        return variant(id, options);
    }

    private RehydratedProductDetails.Variant variant(String id, List<ProductAttribute> options) {
        return new RehydratedProductDetails.Variant(
                id,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                true,
                options.stream()
                        .map(option -> new RehydratedProductDetails.SelectedOption(option.name(), option.value()))
                        .toList(),
                List.of(),
                List.of(),
                List.of());
    }

    private CatalogProductReference reference() {
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("TEST"), ResultSourceType.PROVIDER_CATALOG, "test");
        return new CatalogProductReference(
                "offer",
                source,
                null,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "TEST", "product"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "TEST", "variant"),
                List.of());
    }

    private CatalogProductReference withInteractionKey(
            CatalogProductReference reference,
            String interactionKey
    ) {
        return new CatalogProductReference(
                interactionKey,
                reference.discoverySource(),
                reference.localMerchantId(),
                reference.localRouting(),
                reference.externalMerchantReference(),
                reference.externalMerchantDomain(),
                reference.externalProductReference(),
                reference.externalVariantReference(),
                reference.selectedOptions(),
                reference.components(),
                reference.sellingPlanIdentity()
        );
    }

    private CatalogProductDetailResult currentDetail(
            CatalogProductReference reference,
            RehydratedProductDetails details
    ) {
        ResultFreshness freshness = new ResultFreshness(
                NOW,
                NOW.plus(Duration.ofMinutes(5))
        );
        return CatalogProductDetailResult.from(
                CatalogProductRehydrationResult.fresh(
                        reference,
                        reference,
                        new RehydratedCommercialFacts(
                                "Product",
                                "Merchant",
                                null,
                                OfferAvailability.unknown(),
                                reference.externalVariantReference(),
                                reference.selectedOptions(),
                                List.of(),
                                List.of(),
                                freshness,
                                CommercialFactsFreshness.fromSingleObservation(freshness)
                        )
                ),
                details
        );
    }

    private ProductAttribute option(String name, String value) {
        return new ProductAttribute("variant-option", name, value);
    }
}

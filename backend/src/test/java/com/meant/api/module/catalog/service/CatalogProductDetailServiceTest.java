package com.meant.api.module.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogProductDetailServiceTest {

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
        CatalogProductReference reference = reference();
        RehydratedProductDetails details = details(effective, variants, featured);
        ResultFreshness freshness = new ResultFreshness(
                Instant.parse("2026-07-14T00:00:00Z"),
                Instant.parse("2026-07-14T00:05:00Z"));
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

    private RehydratedProductDetails.Variant variant(List<ProductAttribute> options) {
        String id = options.stream()
                .sorted(java.util.Comparator.comparing(ProductAttribute::name))
                .map(option -> option.name() + "=" + option.value())
                .collect(java.util.stream.Collectors.joining("|"));
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

    private ProductAttribute option(String name, String value) {
        return new ProductAttribute("variant-option", name, value);
    }
}

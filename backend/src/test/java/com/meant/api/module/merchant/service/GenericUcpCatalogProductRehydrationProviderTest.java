package com.meant.api.module.merchant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.properties.GenericUcpCatalogDataUseProperties;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.plugin.catalog.common.dto.ProductDetailsResponse;
import com.meant.api.module.merchant.service.dto.ProductDetailsResult;
import com.meant.api.module.merchant.service.query.GetMerchantProductDetailsQuery;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GenericUcpCatalogProductRehydrationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final UUID MERCHANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID INTEGRATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    private MerchantProductDetailsService detailsService;
    private MerchantIntegrationLookupService integrations;
    private GenericUcpCatalogProductRehydrationProvider provider;
    private AtomicBoolean transactionActive;

    @BeforeEach
    void setUp() {
        detailsService = mock(MerchantProductDetailsService.class);
        integrations = mock(MerchantIntegrationLookupService.class);
        when(integrations.listByMerchants(any())).thenReturn(List.of(integration(MERCHANT_ID, INTEGRATION_ID)));
        when(integrations.listByIds(any())).thenReturn(List.of(integration(MERCHANT_ID, INTEGRATION_ID)));
        GenericUcpCatalogReferenceVerifier verifier = new GenericUcpCatalogReferenceVerifier(integrations);
        GenericUcpCatalogDataUseProperties properties = new GenericUcpCatalogDataUseProperties(
                Duration.ofHours(24), Duration.ofMinutes(2));
        GenericUcpProductObservationMapper mapper = new GenericUcpProductObservationMapper(
                verifier,
                new GenericUcpVariantObservationResolver(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        provider = new GenericUcpCatalogProductRehydrationProvider(detailsService, verifier, mapper);
        transactionActive = new AtomicBoolean();
        when(detailsService.get(any())).thenAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return details("product-1", selected("variant-substitute", "9.99", "S"),
                    List.of(variant("variant-1", "12.99", "M")));
        });
    }

    @Test
    void resolvesRequestedVariantExactlyAndReturnsCanonicalServerRoutingOutsideTransaction() {
        var result = provider.rehydrate(List.of(reference(
                MERCHANT_ID, null, "merchant-1", "variant-1", List.of(option("M")))),
                new CatalogRehydrationContext("CZ", "en")).getFirst();

        assertThat(transactionActive).isFalse();
        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.facts().price().minorUnits()).isEqualTo(1299);
        assertThat(result.facts().selectedVariant().value()).isEqualTo("variant-1");
        assertThat(result.resolvedReference().localRouting().merchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(result.resolvedReference().externalMerchantReference().value()).isEqualTo("merchant-1");
        ArgumentCaptor<GetMerchantProductDetailsQuery> query =
                ArgumentCaptor.forClass(GetMerchantProductDetailsQuery.class);
        verify(detailsService).get(query.capture());
        assertThat(query.getValue().addressCountry()).isEqualTo("CZ");
        assertThat(query.getValue().language()).isEqualTo("en");
    }

    @Test
    void resolvesSessionReferenceByServerRoutingInOneBoundedIntegrationRead() {
        CatalogProductReference first = reference(null, INTEGRATION_ID, "merchant-1", "variant-1", List.of(option("M")));
        CatalogProductReference second = new CatalogProductReference(
                "session-second",
                first.discoverySource(),
                null,
                first.localRouting(),
                first.externalMerchantReference(),
                first.externalProductReference(),
                first.externalVariantReference(),
                first.selectedOptions());

        var results = provider.rehydrate(List.of(first, second), new CatalogRehydrationContext("CZ", "en"));

        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH));
        verify(integrations).listByIds(any());
        verify(integrations, never()).listByMerchants(any());
    }

    @Test
    void missingVariantAndOptionMismatchNeverReturnFreshSubstitute() {
        var results = provider.rehydrate(List.of(
                referenceWithoutVariant(),
                reference(MERCHANT_ID, INTEGRATION_ID, "merchant-1", "missing", List.of()),
                reference(MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of(option("L")))
        ), new CatalogRehydrationContext(null, null));

        assertThat(results).extracting(result -> result.status())
                .containsOnly(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(results).extracting(result -> result.failure())
                .containsExactly(
                        CatalogRehydrationFailureKind.INVALID_REFERENCE,
                        CatalogRehydrationFailureKind.NOT_FOUND,
                        CatalogRehydrationFailureKind.NOT_FOUND
                );
    }

    @Test
    void duplicateVariantIdWithDifferentConfigurationsFailsIndependentOfObservationOrder() {
        for (String selectedSize : List.of("M", "L")) {
            String listedSize = selectedSize.equals("M") ? "L" : "M";
            ProductDetailsResult ambiguous = details(
                    "product-1",
                    selected("variant-1", selectedSize.equals("M") ? "12.99" : "13.99", selectedSize),
                    List.of(variant("variant-1", listedSize.equals("M") ? "12.99" : "13.99", listedSize))
            );
            when(detailsService.get(any())).thenReturn(ambiguous);

            var result = provider.rehydrate(List.of(reference(
                    MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of())),
                    new CatalogRehydrationContext(null, null)).getFirst();

            assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.UNAVAILABLE);
            assertThat(result.failure()).isEqualTo(CatalogRehydrationFailureKind.INVALID_RESPONSE);
            assertThat(result.resolvedReference()).isNull();
        }
    }

    @Test
    void tamperedMerchantAndCrossMerchantRoutingFailBeforeRemoteIo() {
        CatalogProductReference wrongMerchant = reference(
                MERCHANT_ID, INTEGRATION_ID, "merchant-other", "variant-1", List.of());
        CatalogProductReference wrongRouting = reference(
                MERCHANT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                "merchant-1",
                "variant-1",
                List.of()
        );

        var results = provider.rehydrate(List.of(wrongMerchant, wrongRouting),
                new CatalogRehydrationContext(null, null));

        assertThat(results).extracting(result -> result.failure())
                .containsOnly(CatalogRehydrationFailureKind.INVALID_REFERENCE);
        verify(detailsService, never()).get(any());
    }

    @Test
    void providerProductMismatchFailsTypedInsteadOfPersistingRequestedProduct() {
        ProductDetailsResult mismatched = details(
                "different-product",
                selected("variant-1", "12.99", "M"),
                List.of()
        );
        when(detailsService.get(any())).thenReturn(mismatched);

        var result = provider.rehydrate(List.of(reference(
                MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of())),
                new CatalogRehydrationContext(null, null)).getFirst();

        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(result.failure()).isEqualTo(CatalogRehydrationFailureKind.NOT_FOUND);
        assertThat(result.resolvedReference()).isNull();
    }

    private ProductDetailsResult details(
            String productId,
            ProductDetailsResponse.SelectedVariant selected,
            List<ProductDetailsResponse.Variant> variants
    ) {
        ProductDetailsResponse.Product product = mock(ProductDetailsResponse.Product.class);
        when(product.productId()).thenReturn(productId);
        when(product.title()).thenReturn("Current product");
        when(product.imageUrl()).thenReturn("https://merchant.test/product.jpg");
        when(product.images()).thenReturn(List.of());
        when(product.selectedOrFirstAvailableVariant()).thenReturn(selected);
        when(product.variants()).thenReturn(variants);
        return new ProductDetailsResult("https://merchant.test/mcp", "redacted", product);
    }

    private ProductDetailsResponse.SelectedVariant selected(String id, String price, String size) {
        ProductDetailsResponse.SelectedVariant variant = mock(ProductDetailsResponse.SelectedVariant.class);
        when(variant.variantId()).thenReturn(id);
        when(variant.price()).thenReturn(price);
        when(variant.currency()).thenReturn("USD");
        when(variant.available()).thenReturn(true);
        when(variant.selectedOptions()).thenReturn(List.of(new ProductDetailsResponse.SelectedOption("Size", size)));
        return variant;
    }

    private ProductDetailsResponse.Variant variant(String id, String price, String size) {
        ProductDetailsResponse.Variant variant = mock(ProductDetailsResponse.Variant.class);
        when(variant.variantId()).thenReturn(id);
        when(variant.price()).thenReturn(price);
        when(variant.currency()).thenReturn("USD");
        when(variant.available()).thenReturn(true);
        when(variant.selectedOptions()).thenReturn(List.of(new ProductDetailsResponse.SelectedOption("Size", size)));
        return variant;
    }

    private MerchantIntegrationResult integration(UUID merchantId, UUID integrationId) {
        return new MerchantIntegrationResult(
                integrationId,
                merchantId,
                MerchantIntegrationProvider.GENERIC_UCP,
                null,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                "merchant-1",
                "merchant.test",
                null,
                "https://merchant.test/mcp",
                "2026-01-01",
                null,
                MerchantIntegrationStatus.ACTIVE,
                null,
                NOW,
                NOW,
                NOW
        );
    }

    private CatalogProductReference reference(
            UUID merchantId,
            UUID routingId,
            String externalMerchantId,
            String variantId,
            List<ProductAttribute> options
    ) {
        return new CatalogProductReference(
                "saved-1-" + variantId + "-" + externalMerchantId + "-" + routingId,
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                merchantId,
                routingId == null ? null : new LocalMerchantRouting(routingId),
                new ExternalIdentifier(
                        ExternalIdentifierType.MERCHANT,
                        MerchantCatalogSourceIdentity.PROVIDER.value(),
                        externalMerchantId
                ),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        MerchantCatalogSourceIdentity.PROVIDER.value(),
                        "product-1"
                ),
                new ExternalIdentifier(
                        ExternalIdentifierType.VARIANT,
                        MerchantCatalogSourceIdentity.PROVIDER.value(),
                        variantId
                ),
                options
        );
    }

    private ProductAttribute option(String value) {
        return new ProductAttribute("variant-option", "Size", value);
    }

    private CatalogProductReference referenceWithoutVariant() {
        return new CatalogProductReference(
                "saved-without-variant",
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                MERCHANT_ID,
                new LocalMerchantRouting(INTEGRATION_ID),
                new ExternalIdentifier(
                        ExternalIdentifierType.MERCHANT,
                        MerchantCatalogSourceIdentity.PROVIDER.value(),
                        "merchant-1"
                ),
                new ExternalIdentifier(
                        ExternalIdentifierType.PRODUCT,
                        MerchantCatalogSourceIdentity.PROVIDER.value(),
                        "product-1"
                ),
                null,
                List.of()
        );
    }
}

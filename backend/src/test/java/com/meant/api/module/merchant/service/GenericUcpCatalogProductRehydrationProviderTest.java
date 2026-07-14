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
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
        assertThat(result.facts().merchantName()).isEqualTo("Merchant display name");
        assertThat(result.facts().price().minorUnits()).isEqualTo(1299);
        assertThat(result.facts().selectedVariant().value()).isEqualTo("variant-1");
        assertThat(result.resolvedReference().localRouting().merchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(result.resolvedReference().externalMerchantReference().value()).isEqualTo("merchant-1");
        ArgumentCaptor<GetMerchantProductDetailsQuery> query =
                ArgumentCaptor.forClass(GetMerchantProductDetailsQuery.class);
        verify(detailsService).get(query.capture());
        assertThat(query.getValue().addressCountry()).isEqualTo("CZ");
        assertThat(query.getValue().language()).isEqualTo("en");
        assertThat(query.getValue().selectionRequest()).isTrue();
        assertThat(query.getValue().selectedOptions()).containsExactly(option("M"));
        assertThat(query.getValue().preferences()).isEmpty();
    }

    @Test
    void selectionRequestKeepsPartialEffectiveOptionsAndAllCompatibleVariants() {
        ProductDetailsResponse.Product product = mock(ProductDetailsResponse.Product.class);
        List<ProductDetailsResponse.SelectedOption> partialSelection = List.of(
                new ProductDetailsResponse.SelectedOption("Color", "Blue"));
        List<ProductDetailsResponse.SelectedOption> blueMedium = List.of(
                new ProductDetailsResponse.SelectedOption("Color", "Blue"),
                new ProductDetailsResponse.SelectedOption("Size", "M"));
        List<ProductDetailsResponse.SelectedOption> blueLarge = List.of(
                new ProductDetailsResponse.SelectedOption("Color", "Blue"),
                new ProductDetailsResponse.SelectedOption("Size", "L"));
        ProductDetailsResponse.SelectedVariant featured = selected(
                "variant-blue-m", "12.99", blueMedium);
        List<ProductDetailsResponse.Variant> variants = List.of(
                variant("variant-blue-m", "12.99", blueMedium),
                variant("variant-blue-l", "13.99", blueLarge));
        when(product.productId()).thenReturn("product-1");
        when(product.title()).thenReturn("Current product");
        when(product.selected()).thenReturn(partialSelection);
        when(product.selectedOrFirstAvailableVariant()).thenReturn(featured);
        when(product.variants()).thenReturn(variants);
        when(detailsService.get(any())).thenReturn(new ProductDetailsResult(
                "https://merchant.test/mcp", "redacted", product));
        CatalogProductDetailSelection selection = new CatalogProductDetailSelection(
                List.of(new ProductAttribute("variant-option", "Color", "Blue")),
                List.of("Prefer cotton"));

        CatalogProductDetailResult result = provider.getDetails(
                reference(MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of(option("M"))),
                selection,
                new CatalogRehydrationContext("CZ", "en"));

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().resolvedReference().externalVariantReference().value())
                .isEqualTo("variant-blue-m");
        assertThat(result.details().selected()).containsExactly(
                new com.meant.api.module.catalog.service.dto.RehydratedProductDetails.SelectedOption(
                        "Color", "Blue"));
        assertThat(result.details().variants()).extracting(variant -> variant.variantId())
                .containsExactly("variant-blue-m", "variant-blue-l");
        ArgumentCaptor<GetMerchantProductDetailsQuery> query =
                ArgumentCaptor.forClass(GetMerchantProductDetailsQuery.class);
        verify(detailsService).get(query.capture());
        assertThat(query.getValue().selectionRequest()).isTrue();
        assertThat(query.getValue().selectedOptions()).isEqualTo(selection.selectedOptions());
        assertThat(query.getValue().preferences()).containsExactly("Prefer cotton");
    }

    @Test
    void returnsAllCurrentGetProductFieldsAsTransientSavedDetail() {
        ProductDetailsResponse.SelectedVariant selected = new ProductDetailsResponse.SelectedVariant(
                "variant-1",
                "Large",
                "12.99",
                "USD",
                "sku-large",
                "15.99",
                "https://merchant.test/large.jpg",
                "Large shirt",
                List.of(new ProductDetailsResponse.Media(
                        "image", "https://merchant.test/large.jpg", "Large shirt", null)),
                true,
                List.of(new ProductDetailsResponse.SelectedOption("Size", "M"))
        );
        ProductDetailsResponse.Variant variant = new ProductDetailsResponse.Variant(
                "variant-1",
                "large",
                "Large",
                "Large variant description",
                "https://merchant.test/products/product-1?variant=variant-1",
                "12.99",
                "USD",
                "sku-large",
                "15.99",
                "https://merchant.test/large.jpg",
                "Large shirt",
                List.of(new ProductDetailsResponse.Media(
                        "image", "https://merchant.test/large.jpg", "Large shirt", null)),
                true,
                List.of(new ProductDetailsResponse.SelectedOption("Size", "M")),
                List.of(new ProductDetailsResponse.Category("Shirts", "apparel")),
                List.of("organic", "summer"),
                Map.of("Weight", "180 gsm")
        );
        ProductDetailsResponse.Product product = new ProductDetailsResponse.Product(
                "product-1",
                "perfect-shirt",
                "Perfect shirt",
                "Full current product description",
                "https://merchant.test/products/product-1",
                "https://merchant.test/product.jpg",
                List.of(new ProductDetailsResponse.Image(
                        "https://merchant.test/product.jpg", "Perfect shirt")),
                List.of(new ProductDetailsResponse.Media(
                        "video", "https://merchant.test/product.mp4", "Product video",
                        "https://merchant.test/video-preview.jpg")),
                List.of(new ProductDetailsResponse.Category("Shirts", "apparel")),
                List.of("organic", "summer"),
                List.of(new ProductDetailsResponse.Option("Size", List.of("S", "M", "L"))),
                List.of(variant),
                3,
                new ProductDetailsResponse.PriceRange("9.99", "12.99", "USD"),
                new ProductDetailsResponse.PriceRange("14.99", "15.99", "USD"),
                null,
                Map.of("ratingValue", 4.6d),
                Map.of("reviewCount", 321),
                false,
                List.of(),
                List.of("sku-small", "sku-large"),
                List.of("GOTS"),
                List.of("Organic cotton"),
                List.of("Essentials"),
                Map.of("Fit", "Regular"),
                Map.of("Care", "Cold wash"),
                Map.of("Weight", "180 gsm"),
                selected
        );
        ProductDetailsResponse.Message message = new ProductDetailsResponse.Message(
                "info",
                "FIT_NOTE",
                "/variants/variant-1",
                "text/plain",
                "True to size",
                "info",
                "inline",
                null,
                null
        );
        when(detailsService.get(any())).thenReturn(new ProductDetailsResult(
                "https://merchant.test/mcp",
                "redacted",
                product,
                List.of(message),
                com.meant.api.plugin.spi.NegotiatedCapabilities.none()
        ));

        CatalogProductDetailResult result = provider.getDetails(
                reference(MERCHANT_ID, null, "merchant-1", "variant-1", List.of(option("M"))),
                new CatalogRehydrationContext("CZ", "en")
        );

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.details().description()).isEqualTo("Full current product description");
        assertThat(result.details().images()).singleElement()
                .extracting(image -> image.url()).isEqualTo("https://merchant.test/product.jpg");
        assertThat(result.details().media()).singleElement()
                .extracting(media -> media.type()).isEqualTo("video");
        assertThat(result.details().options()).singleElement().satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size");
            assertThat(option.values()).containsExactly("S", "M", "L");
        });
        assertThat(result.details().variants()).singleElement().satisfies(detailVariant -> {
            assertThat(detailVariant.variantId()).isEqualTo("variant-1");
            assertThat(detailVariant.description()).isEqualTo("Large variant description");
            assertThat(detailVariant.attributes()).extracting(attribute -> attribute.value())
                    .contains("180 gsm");
        });
        assertThat(result.details().selectedVariant().sku()).isEqualTo("sku-large");
        assertThat(result.details().certifications()).containsExactly("GOTS");
        assertThat(result.details().materials()).containsExactly("Organic cotton");
        assertThat(result.details().collections()).containsExactly("Essentials");
        assertThat(result.details().messages()).singleElement()
                .extracting(detailMessage -> detailMessage.content()).isEqualTo("True to size");
        assertThat(result.details().ratingScore()).isEqualTo(4.6d);
        assertThat(result.details().ratingScaleMax()).isEqualTo(5.0d);
        assertThat(result.details().reviewCount()).isEqualTo(321L);
        assertThat(result.details().merchantName()).isEqualTo("Merchant display name");
        verify(detailsService).get(new GetMerchantProductDetailsQuery(MERCHANT_ID, "product-1", "CZ", "en"));
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
    void rehydratesShopifyMerchantIntegrationResultsThroughTheExistingStorefrontPath() {
        MerchantIntegrationResult shopify = integration(
                MERCHANT_ID, INTEGRATION_ID, MerchantIntegrationProvider.SHOPIFY);
        when(integrations.listByIds(any())).thenReturn(List.of(shopify));
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity(MerchantIntegrationProvider.SHOPIFY.name()),
                ResultSourceType.MERCHANT_STOREFRONT,
                "merchant-1"
        );
        CatalogProductReference reference = new CatalogProductReference(
                "shopify-offer",
                source,
                null,
                new LocalMerchantRouting(INTEGRATION_ID),
                identifier(MerchantIntegrationProvider.SHOPIFY, ExternalIdentifierType.MERCHANT, "merchant-1"),
                "merchant.test",
                identifier(MerchantIntegrationProvider.SHOPIFY, ExternalIdentifierType.PRODUCT, "product-1"),
                identifier(MerchantIntegrationProvider.SHOPIFY, ExternalIdentifierType.VARIANT, "variant-1"),
                List.of(option("M"))
        );

        var result = provider.rehydrate(
                List.of(reference), new CatalogRehydrationContext("CZ", "en")).getFirst();

        assertThat(provider.supports(source)).isTrue();
        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.resolvedReference().discoverySource()).isEqualTo(source);
        assertThat(result.resolvedReference().localRouting().merchantIntegrationId()).isEqualTo(INTEGRATION_ID);
        assertThat(result.resolvedReference().externalMerchantDomain()).isEqualTo("merchant.test");
        assertThat(result.facts().selectedVariant().namespace()).isEqualTo("SHOPIFY");
        assertThat(result.facts().selectedVariant().value()).isEqualTo("variant-1");
        verify(detailsService).get(new GetMerchantProductDetailsQuery(
                MERCHANT_ID,
                "product-1",
                "CZ",
                "en",
                reference.selectedOptions(),
                List.of()));
    }

    @Test
    void rehydratesThePerMerchantSourceProducedByCurrentSemanticDiscovery() {
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                MerchantCatalogSourceIdentity.PROVIDER,
                ResultSourceType.MERCHANT_STOREFRONT,
                "merchant-1"
        );
        CatalogProductReference reference = new CatalogProductReference(
                "semantic-offer",
                source,
                null,
                new LocalMerchantRouting(INTEGRATION_ID),
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.MERCHANT, "merchant-1"),
                "merchant.test",
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.PRODUCT, "product-1"),
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.VARIANT, "variant-1"),
                List.of(option("M"))
        );

        var result = provider.rehydrate(
                List.of(reference), new CatalogRehydrationContext("CZ", "en")).getFirst();

        assertThat(provider.supports(source)).isTrue();
        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.resolvedReference().discoverySource()).isEqualTo(source);
        assertThat(result.resolvedReference().externalMerchantDomain()).isEqualTo("merchant.test");
        assertThat(result.facts().selectedVariant().namespace()).isEqualTo("GENERIC_UCP");
    }

    @Test
    void rejectsForgedPerMerchantSourceAndDomainBeforeRemoteIo() {
        CatalogProductReference valid = new CatalogProductReference(
                "semantic-offer",
                new DiscoverySourceIdentity(
                        MerchantCatalogSourceIdentity.PROVIDER,
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "merchant-1"),
                null,
                new LocalMerchantRouting(INTEGRATION_ID),
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.MERCHANT, "merchant-1"),
                "merchant.test",
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.PRODUCT, "product-1"),
                identifier(MerchantIntegrationProvider.GENERIC_UCP, ExternalIdentifierType.VARIANT, "variant-1"),
                List.of(option("M"))
        );
        CatalogProductReference forgedSource = new CatalogProductReference(
                "forged-source",
                new DiscoverySourceIdentity(
                        MerchantCatalogSourceIdentity.PROVIDER,
                        ResultSourceType.MERCHANT_STOREFRONT,
                        "different-merchant"),
                null,
                valid.localRouting(),
                valid.externalMerchantReference(),
                valid.externalMerchantDomain(),
                valid.externalProductReference(),
                valid.externalVariantReference(),
                valid.selectedOptions()
        );
        CatalogProductReference forgedDomain = new CatalogProductReference(
                "forged-domain",
                valid.discoverySource(),
                null,
                valid.localRouting(),
                valid.externalMerchantReference(),
                "attacker.test",
                valid.externalProductReference(),
                valid.externalVariantReference(),
                valid.selectedOptions()
        );

        var results = provider.rehydrate(
                List.of(forgedSource, forgedDomain), new CatalogRehydrationContext("CZ", "en"));

        assertThat(results).extracting(CatalogProductRehydrationResult::failure)
                .containsOnly(CatalogRehydrationFailureKind.INVALID_REFERENCE);
        verify(detailsService, never()).get(any());
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

    @Test
    void requiredGenericSellingPlanFailsClosedWithoutExactTypedIdentity() {
        ProductDetailsResult requiringPlan = details(
                "product-1",
                selected("variant-1", "12.99", "M"),
                List.of()
        );
        when(requiringPlan.product().requiresSellingPlan()).thenReturn(true);
        when(detailsService.get(any())).thenReturn(requiringPlan);
        CatalogProductReference withoutPlan = reference(
                MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of(option("M")));

        CatalogProductRehydrationResult rejected = provider.rehydrate(
                List.of(withoutPlan), new CatalogRehydrationContext(null, null)).getFirst();

        assertThat(rejected.status()).isEqualTo(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(rejected.failure()).isEqualTo(CatalogRehydrationFailureKind.INVALID_REFERENCE);

        SellingPlanIdentity plan = new SellingPlanIdentity(
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.SELLING_PLAN_GROUP, "subscriptions"),
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.SELLING_PLAN, "monthly"),
                List.of(new SellingPlanOption("frequency", "monthly"))
        );
        CatalogProductReference withPlan = new CatalogProductReference(
                withoutPlan.interactionKey(),
                withoutPlan.discoverySource(),
                withoutPlan.localMerchantId(),
                withoutPlan.localRouting(),
                withoutPlan.externalMerchantReference(),
                withoutPlan.externalMerchantDomain(),
                withoutPlan.externalProductReference(),
                withoutPlan.externalVariantReference(),
                withoutPlan.selectedOptions(),
                List.of(),
                plan
        );

        CatalogProductRehydrationResult configured = provider.rehydrate(
                List.of(withPlan), new CatalogRehydrationContext(null, null)).getFirst();
        assertThat(configured.status()).isEqualTo(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(configured.failure()).isEqualTo(CatalogRehydrationFailureKind.INVALID_REFERENCE);
        assertThat(configured.resolvedReference()).isNull();
    }

    @Test
    void configuredGenericOffersFailClosedWhenGetProductCannotVerifyTheirExactIdentity() {
        CatalogProductReference base = reference(
                MERCHANT_ID, INTEGRATION_ID, "merchant-1", "variant-1", List.of(option("M")));
        SellingPlanIdentity plan = new SellingPlanIdentity(
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.SELLING_PLAN_GROUP, "subscriptions"),
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.SELLING_PLAN, "monthly"),
                List.of(new SellingPlanOption("frequency", "monthly"))
        );
        OfferComponentIdentity component = new OfferComponentIdentity(
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.PRODUCT, "component-product"),
                identifier(MerchantIntegrationProvider.GENERIC_UCP,
                        ExternalIdentifierType.VARIANT, "component-variant"),
                2,
                List.of(option("S"))
        );
        CatalogProductReference withPlan = configuredReference(base, List.of(), plan);
        CatalogProductReference withComponent = configuredReference(base, List.of(component), null);

        List<CatalogProductRehydrationResult> results = provider.rehydrate(
                List.of(withPlan, withComponent), new CatalogRehydrationContext(null, null));

        assertThat(results).extracting(CatalogProductRehydrationResult::status)
                .containsOnly(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(results).extracting(CatalogProductRehydrationResult::failure)
                .containsOnly(CatalogRehydrationFailureKind.INVALID_REFERENCE);
        assertThat(results).extracting(CatalogProductRehydrationResult::resolvedReference)
                .containsOnlyNulls();
    }

    private CatalogProductReference configuredReference(
            CatalogProductReference base,
            List<OfferComponentIdentity> components,
            SellingPlanIdentity sellingPlan
    ) {
        return new CatalogProductReference(
                base.interactionKey() + "-configured-" + components.size() + "-" + (sellingPlan != null),
                base.discoverySource(),
                base.localMerchantId(),
                base.localRouting(),
                base.externalMerchantReference(),
                base.externalMerchantDomain(),
                base.externalProductReference(),
                base.externalVariantReference(),
                base.selectedOptions(),
                components,
                sellingPlan
        );
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
        return selected(
                id,
                price,
                List.of(new ProductDetailsResponse.SelectedOption("Size", size)));
    }

    private ProductDetailsResponse.SelectedVariant selected(
            String id,
            String price,
            List<ProductDetailsResponse.SelectedOption> options
    ) {
        ProductDetailsResponse.SelectedVariant variant = mock(ProductDetailsResponse.SelectedVariant.class);
        when(variant.variantId()).thenReturn(id);
        when(variant.price()).thenReturn(price);
        when(variant.currency()).thenReturn("USD");
        when(variant.available()).thenReturn(true);
        when(variant.selectedOptions()).thenReturn(options);
        return variant;
    }

    private ProductDetailsResponse.Variant variant(String id, String price, String size) {
        return variant(
                id,
                price,
                List.of(new ProductDetailsResponse.SelectedOption("Size", size)));
    }

    private ProductDetailsResponse.Variant variant(
            String id,
            String price,
            List<ProductDetailsResponse.SelectedOption> options
    ) {
        ProductDetailsResponse.Variant variant = mock(ProductDetailsResponse.Variant.class);
        when(variant.variantId()).thenReturn(id);
        when(variant.price()).thenReturn(price);
        when(variant.currency()).thenReturn("USD");
        when(variant.available()).thenReturn(true);
        when(variant.selectedOptions()).thenReturn(options);
        return variant;
    }

    private MerchantIntegrationResult integration(UUID merchantId, UUID integrationId) {
        return integration(merchantId, integrationId, MerchantIntegrationProvider.GENERIC_UCP);
    }

    private MerchantIntegrationResult integration(
            UUID merchantId,
            UUID integrationId,
            MerchantIntegrationProvider provider
    ) {
        return new MerchantIntegrationResult(
                integrationId,
                merchantId,
                "Merchant display name",
                provider,
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

    private ExternalIdentifier identifier(
            MerchantIntegrationProvider provider,
            ExternalIdentifierType type,
            String value
    ) {
        return new ExternalIdentifier(type, provider.name(), value);
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

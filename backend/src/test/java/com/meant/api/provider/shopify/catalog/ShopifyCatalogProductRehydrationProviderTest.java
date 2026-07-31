package com.meant.api.provider.shopify.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meant.api.module.catalog.service.CommercialFreshnessPolicy;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailSelection;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationFailureKind;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationStatus;
import com.meant.api.module.catalog.service.dto.CatalogSourceResult;
import com.meant.api.module.catalog.service.dto.CommercialFact;
import com.meant.api.module.catalog.service.dto.CommercialFreshnessStatus;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProductAttribution;
import com.meant.api.module.catalog.service.dto.ProductCandidate;
import com.meant.api.module.catalog.service.dto.ProductCertification;
import com.meant.api.module.catalog.service.dto.ProductMaterial;
import com.meant.api.module.catalog.service.dto.ProductMedia;
import com.meant.api.module.catalog.service.dto.ProductMediaType;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogGetProductRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogLookupRequest;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogProductResult;
import com.meant.api.provider.shopify.catalog.dto.ShopifyGlobalCatalogResponse;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShopifyCatalogProductRehydrationProviderTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final ProviderIdentity SHOPIFY = new ProviderIdentity("SHOPIFY");
    private static final DiscoverySourceIdentity SOURCE = new DiscoverySourceIdentity(
            SHOPIFY,
            ResultSourceType.PROVIDER_CATALOG,
            "SHOPIFY_GLOBAL_CATALOG"
    );

    @Test
    void matchesExactSellerForSameProductAndVariantUnderBothCandidateOrders() {
        for (List<String> candidateOrder : List.of(List.of("seller-a", "seller-b"), List.of("seller-b", "seller-a"))) {
            for (List<String> requestedOrder : List.of(
                    List.of("seller-a", "seller-b"),
                    List.of("seller-b", "seller-a"))) {
                ShopifyGlobalCatalogProvider global = providerSource(50);
                CatalogSourceResult sourceResult = successful(candidateOrder.stream()
                        .map(seller -> candidate("product-1", "variant-1", seller,
                                seller.equals("seller-a") ? 1000 : 2500, available()))
                        .toList());
                when(global.lookupCatalog(any())).thenReturn(sourceResult);
                ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);

                var results = provider.rehydrate(requestedOrder.stream()
                                .map(seller -> reference(
                                        "saved-" + seller, "product-1", "variant-1", seller, List.of()))
                                .toList(),
                        new CatalogRehydrationContext("CZ", "en"));

                assertThat(results).allSatisfy(result -> {
                    String seller = result.resolvedReference().externalMerchantReference().value();
                    assertThat(result.facts().merchantName()).isEqualTo(seller);
                    assertThat(result.facts().price().minorUnits())
                            .isEqualTo(seller.equals("seller-a") ? 1000L : 2500L);
                });
            }
        }
    }

    @Test
    void enrichesAnOmittedMerchantDomainButRejectsAConflictingDomain() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult sourceResult = successful(List.of(candidate(
                "product-1", "variant-1", "seller-a", "seller.example", 1000, available())));
        when(global.lookupCatalog(any())).thenReturn(sourceResult);
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);
        CatalogProductReference withoutDomain = reference(
                "without-domain", "product-1", "variant-1", "seller-a", List.of());
        CatalogProductReference conflictingDomain = new CatalogProductReference(
                "wrong-domain",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "other.example",
                product("product-1"),
                variant("variant-1"),
                List.of()
        );

        var results = provider.rehydrate(
                List.of(withoutDomain, conflictingDomain),
                new CatalogRehydrationContext(null, null)
        );

        assertThat(results.getFirst().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(results.getFirst().resolvedReference().externalMerchantDomain())
                .isEqualTo("seller.example");
        assertThat(results.getLast().status()).isEqualTo(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(results.getLast().failure()).isEqualTo(CatalogRehydrationFailureKind.NOT_FOUND);
    }

    @Test
    void rejectsWrongMerchantVariantOptionsAndClientRouting() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult sourceResult = successful(List.of(candidate(
                "product-1",
                "variant-1",
                "seller-a",
                1000,
                available()
        )));
        when(global.lookupCatalog(any())).thenReturn(sourceResult);
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);
        ProductAttribute size = new ProductAttribute("variant-option", "Size", "M");

        var results = provider.rehydrate(List.of(
                reference("merchant", "product-1", "variant-1", "seller-b", List.of()),
                reference("variant", "product-1", "variant-2", "seller-a", List.of()),
                reference("options", "product-1", "variant-1", "seller-a", List.of(size)),
                new CatalogProductReference(
                        "routing",
                        SOURCE,
                        java.util.UUID.randomUUID(),
                        null,
                        merchant("seller-a"),
                        product("product-1"),
                        variant("variant-1"),
                        List.of()
                )
        ), new CatalogRehydrationContext(null, null));

        assertThat(results).extracting(result -> result.status())
                .containsOnly(CatalogRehydrationStatus.UNAVAILABLE);
        assertThat(results.get(3).failure()).isEqualTo(CatalogRehydrationFailureKind.INVALID_REFERENCE);
    }

    @Test
    void isolatesFailedLookupChunkFromSuccessfulChunk() {
        ShopifyGlobalCatalogProvider global = providerSource(1);
        when(global.lookupCatalog(any())).thenAnswer(invocation -> {
            ShopifyGlobalCatalogLookupRequest request = invocation.getArgument(0);
            return request.ids().contains("variant-ok")
                    ? successful(List.of(candidate(
                            "product-ok", "variant-ok", "seller", 1200, available())))
                    : throwFailure();
        });
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 1);

        var results = provider.rehydrate(List.of(
                reference("ok", "product-ok", "variant-ok", "seller", List.of()),
                reference("failed", "product-failed", "variant-failed", "seller", List.of())
        ), new CatalogRehydrationContext(null, null));

        assertThat(results.getFirst().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(results.getLast().status()).isEqualTo(CatalogRehydrationStatus.DEGRADED);
        assertThat(results.getLast().failure()).isEqualTo(CatalogRehydrationFailureKind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void unknownAvailabilityDoesNotReceiveCurrentFreshness() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult sourceResult = successful(List.of(candidate(
                "product-1", "variant-1", "seller", 1000, OfferAvailability.unknown())));
        when(global.lookupCatalog(any())).thenReturn(sourceResult);

        var result = rehydrator(global, 50).rehydrate(
                List.of(reference("saved", "product-1", "variant-1", "seller", List.of())),
                new CatalogRehydrationContext(null, null)
        ).getFirst();

        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.facts().purchaseFreshness().availability()).isNull();
        assertThat(new CommercialFreshnessPolicy().decide(
                result.facts().purchaseFreshness(),
                EnumSet.of(CommercialFact.AVAILABILITY),
                NOW
        ).status()).isEqualTo(CommercialFreshnessStatus.REFRESH_REQUIRED);
    }

    @Test
    void propagatesAvailableCountryLanguageAndCurrencyToShopifyLookup() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult lookupResult = successful(List.of(candidate(
                "product-1", "variant-1", "seller", 1000, available())));
        when(global.lookupCatalog(any())).thenReturn(lookupResult);

        rehydrator(global, 50).rehydrate(
                List.of(reference("saved", "product-1", "variant-1", "seller", List.of())),
                new CatalogRehydrationContext("CZ", "cs", "EUR")
        );

        ArgumentCaptor<ShopifyGlobalCatalogLookupRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogLookupRequest.class);
        org.mockito.Mockito.verify(global).lookupCatalog(request.capture());
        assertThat(request.getValue().context().addressCountry()).isEqualTo("CZ");
        assertThat(request.getValue().context().language()).isEqualTo("cs");
        assertThat(request.getValue().context().currency()).isEqualTo("EUR");
    }

    @Test
    void savedDetailUsesGetProductAndReturnsOnlyTheExactSellersFullVariants() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        ProductCandidate selected = detailedCandidate(
                "product-1", "variant-m", "seller-a", "seller.example", "M", 1299);
        ProductCandidate sibling = detailedCandidate(
                "product-1", "variant-l", "seller-a", "seller.example", "L", 1399);
        ProductCandidate otherSeller = detailedCandidate(
                "product-1", "variant-m", "seller-b", "other.example", "M", 999);
        CatalogSourceResult productResult = successful(List.of(selected, sibling, otherSeller));
        ShopifyGlobalCatalogProductResult detailResult = new ShopifyGlobalCatalogProductResult(
                productResult,
                rawProduct(),
                List.of(new ShopifyGlobalCatalogResponse.Message(
                        "info",
                        "FIT_NOTE",
                        "/variants/variant-m",
                        "text/plain",
                        "True to size",
                        "info",
                        "inline",
                        null,
                        null
                ))
        );
        when(global.getProductWithDetails(any())).thenReturn(detailResult);
        CatalogProductReference requested = new CatalogProductReference(
                "saved-product",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-m"),
                List.of(new ProductAttribute("variant-option", "Size", "M"))
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                requested,
                new CatalogRehydrationContext("CZ", "cs")
        );

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().resolvedReference().externalMerchantReference())
                .isEqualTo(merchant("seller-a"));
        assertThat(result.rehydration().resolvedReference().externalMerchantDomain())
                .isEqualTo("seller.example");
        assertThat(result.rehydration().resolvedReference().externalVariantReference())
                .isEqualTo(variant("variant-m"));
        assertThat(result.details().handle()).isEqualTo("perfect-shirt");
        assertThat(result.details().description()).isEqualTo("Full current product description");
        assertThat(result.details().media()).singleElement().satisfies(media -> {
            assertThat(media.type()).isEqualTo("image");
            assertThat(media.url()).isEqualTo("https://seller.example/product.jpg");
        });
        assertThat(result.details().options()).singleElement().satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size");
            assertThat(option.values()).containsExactly("M", "L");
        });
        assertThat(result.details().variants())
                .extracting(detailVariant -> detailVariant.variantId())
                .containsExactly("variant-m", "variant-l");
        assertThat(result.details().selectedVariant().variantId()).isEqualTo("variant-m");
        assertThat(result.details().selectedVariant().handle()).isEqualTo("medium");
        assertThat(result.details().selectedVariant().description()).isEqualTo("Medium variant description");
        assertThat(result.details().selectedVariant().sku()).isEqualTo("sku-medium");
        assertThat(result.details().tags()).containsExactly("organic", "summer");
        assertThat(result.details().attributes()).extracting(attribute -> attribute.value())
                .contains("180 gsm", "Machine washable", "Made for travel");
        assertThat(result.details().messages()).singleElement()
                .extracting(message -> message.content()).isEqualTo("True to size");
        assertThat(result.details().ratingScore()).isEqualTo(4.7d);
        assertThat(result.details().ratingScaleMax()).isEqualTo(5.0d);
        assertThat(result.details().reviewCount()).isEqualTo(84L);
        assertThat(result.details().merchantName()).isEqualTo("Seller A");

        CatalogProductReference legacyReference = new CatalogProductReference(
                "legacy-saved-product",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                null,
                product("product-1"),
                variant("variant-m"),
                List.of()
        );
        CatalogProductDetailResult legacyResult = rehydrator(global, 50).getDetails(
                legacyReference,
                new CatalogRehydrationContext("CZ", "cs")
        );
        assertThat(legacyResult.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(legacyResult.rehydration().resolvedReference().externalMerchantDomain())
                .isEqualTo("seller.example");
        assertThat(legacyResult.rehydration().resolvedReference().selectedOptions())
                .containsExactly(new ProductAttribute("variant-option", "Size", "M"));
        assertThat(legacyResult.details().variants()).hasSize(2);
        assertThat(legacyResult.details().selectedVariant().sku()).isEqualTo("sku-medium");

        verify(global, never()).lookupCatalog(any());
        ArgumentCaptor<ShopifyGlobalCatalogGetProductRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogGetProductRequest.class);
        verify(global, times(2)).getProductWithDetails(request.capture());
        ShopifyGlobalCatalogGetProductRequest currentRequest = request.getAllValues().getFirst();
        assertThat(currentRequest.id()).isEqualTo("variant-m");
        assertThat(currentRequest.selected()).singleElement().satisfies(option -> {
            assertThat(option.name()).isEqualTo("Size");
            assertThat(option.label()).isEqualTo("M");
        });
        assertThat(currentRequest.context().addressCountry()).isEqualTo("CZ");
        assertThat(currentRequest.context().language()).isEqualTo("cs");
        assertThat(request.getAllValues().getLast().selected()).isEmpty();
    }

    @Test
    void sanitizesBuyerVisibleMessagesWithoutTrustingShopifySellerCoordinatesAsOrigin() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        ProductCandidate selected = detailedCandidate(
                "product-1", "variant-m", "seller-a", "seller.example", "M", 1299);
        ProductCandidate sibling = detailedCandidate(
                "product-1", "variant-l", "seller-a", "seller.example", "L", 1399);
        CatalogSourceResult productResult = successful(List.of(selected, sibling));
        ShopifyGlobalCatalogResponse.Product product = rawProduct();
        ShopifyGlobalCatalogResponse.Seller technicalSeller = new ShopifyGlobalCatalogResponse.Seller(
                "Seller A",
                "seller-a",
                "seller.example",
                "https://seller-profile.transport.test/storefront",
                List.of()
        );
        product = withVariants(
                product,
                product.variants().stream()
                        .map(variant -> "seller-a".equals(variant.seller().id())
                                ? withSeller(variant, technicalSeller)
                                : variant)
                        .toList()
        );
        ShopifyGlobalCatalogResponse.Message technical = new ShopifyGlobalCatalogResponse.Message(
                "https://catalog.test/mcp",
                "https://seller.example/mcp",
                "/mcp",
                "https://seller-profile.transport.test/storefront",
                "Retry https://catalog.test/mcp, seller.example, or seller-profile.transport.test",
                "https://catalog.test/mcp",
                "https://seller.example/mcp",
                "https://seller-profile.transport.test/private.png",
                "https://seller.example/help"
        );
        ShopifyGlobalCatalogResponse.Message legitimate = new ShopifyGlobalCatalogResponse.Message(
                "INFO",
                "CARE_GUIDE",
                "/product/care",
                "text/plain",
                "Read the independent care guide",
                "INFO",
                "INLINE",
                "https://cdn.example/care.png",
                "https://brand.example/care"
        );
        ShopifyGlobalCatalogProductResult detailsResult = new ShopifyGlobalCatalogProductResult(
                productResult,
                product,
                List.of(technical, legitimate)
        );
        when(global.getProductWithDetails(any())).thenReturn(detailsResult);
        CatalogProductReference requested = new CatalogProductReference(
                "saved-product-message-sanitization",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-m"),
                List.of(new ProductAttribute("variant-option", "Size", "M"))
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                requested,
                new CatalogRehydrationContext("CZ", "en")
        );

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.details().messages()).hasSize(2);
        assertThat(result.details().messages().getFirst()).satisfies(message -> {
            assertThat(message.type()).isEqualTo("notice");
            assertThat(message.code()).isNull();
            assertThat(message.path()).isNull();
            assertThat(message.contentType()).isEqualTo("text/plain");
            assertThat(message.severity()).isNull();
            assertThat(message.presentation()).isEqualTo("inline");
            assertThat(message.imageUrl()).isNull();
            assertThat(message.url()).isNull();
            assertThat(message.content())
                    .contains("the merchant")
                    .doesNotContain("catalog.test")
                    .doesNotContain("seller.example")
                    .doesNotContain("seller-profile.transport.test");
        });
        assertThat(result.details().messages().getLast()).satisfies(message -> {
            assertThat(message.type()).isEqualTo("info");
            assertThat(message.content()).isEqualTo("Read the independent care guide");
            assertThat(message.imageUrl()).isEqualTo("https://cdn.example/care.png");
            assertThat(message.url()).isEqualTo("https://brand.example/care");
        });
    }

    @Test
    void partialSelectionReturnsCompatibleVariantsAndRequestsTheAnchoredShopIncludingUnavailableValues() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        ShopifyGlobalCatalogResponse.Product base = rawProduct();
        List<ShopifyGlobalCatalogResponse.SelectedOption> blueMedium = List.of(
                new ShopifyGlobalCatalogResponse.SelectedOption("Color", "Blue"),
                new ShopifyGlobalCatalogResponse.SelectedOption("Size", "M"));
        List<ShopifyGlobalCatalogResponse.SelectedOption> blueLarge = List.of(
                new ShopifyGlobalCatalogResponse.SelectedOption("Color", "Blue"),
                new ShopifyGlobalCatalogResponse.SelectedOption("Size", "L"));
        ShopifyGlobalCatalogResponse.Product selectionProduct = new ShopifyGlobalCatalogResponse.Product(
                base.id(),
                base.handle(),
                base.title(),
                base.description(),
                base.url(),
                base.categories(),
                base.priceRange(),
                base.listPriceRange(),
                base.media(),
                List.of(
                        new ShopifyGlobalCatalogResponse.Option("Color", List.of(
                                new ShopifyGlobalCatalogResponse.OptionValue("Blue", true, true),
                                new ShopifyGlobalCatalogResponse.OptionValue("Red", false, true))),
                        new ShopifyGlobalCatalogResponse.Option("Size", List.of(
                                new ShopifyGlobalCatalogResponse.OptionValue("M", true, true),
                                new ShopifyGlobalCatalogResponse.OptionValue("L", false, true)))),
                List.of(new ShopifyGlobalCatalogResponse.SelectedOption("Color", "Blue")),
                List.of(
                        withOptions(base.variants().get(0), blueMedium),
                        withOptions(base.variants().get(1), blueLarge),
                        withOptions(base.variants().get(2), blueMedium)),
                9,
                base.rating(),
                base.tags(),
                base.metadata()
        );
        CatalogSourceResult sourceResult = successful(List.of());
        ShopifyGlobalCatalogProductResult detailResult = new ShopifyGlobalCatalogProductResult(
                sourceResult, selectionProduct, List.of());
        when(global.getProductWithDetails(any())).thenReturn(detailResult);
        CatalogProductReference anchor = new CatalogProductReference(
                "saved-product",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-m"),
                List.of(new ProductAttribute("variant-option", "Size", "M"))
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                anchor,
                new CatalogProductDetailSelection(
                        List.of(new ProductAttribute("variant-option", "Color", "Blue")),
                        List.of("Prefer cotton")),
                new CatalogRehydrationContext("CZ", "cs"));

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().resolvedReference().externalVariantReference()).isEqualTo(variant("variant-m"));
        assertThat(result.details().selected()).containsExactly(
                new com.meant.api.module.catalog.service.dto.RehydratedProductDetails.SelectedOption(
                        "Color", "Blue"));
        assertThat(result.details().variants()).extracting(detailVariant -> detailVariant.variantId())
                .containsExactly("variant-m", "variant-l");
        assertThat(result.details().totalVariants()).isEqualTo(9);
        assertThat(result.details().options().getFirst().valueDetails().getLast().available()).isFalse();
        ArgumentCaptor<ShopifyGlobalCatalogGetProductRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogGetProductRequest.class);
        verify(global).getProductWithDetails(request.capture());
        assertThat(request.getValue().id()).isEqualTo("product-1");
        assertThat(request.getValue().selected()).containsExactly(
                new com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogSelectedOption("Color", "Blue"));
        assertThat(request.getValue().preferences()).containsExactly("Prefer cotton");
        assertThat(request.getValue().filters().available()).isFalse();
        assertThat(request.getValue().filters().shops()).containsExactly("seller-a");
    }

    @Test
    void completeSelectionUsesProductIdentityAndResolvesTheExactSiblingVariant() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        String productId = "gid://shopify/p/upid-1";
        String mediumVariantId = "gid://shopify/ProductVariant/100";
        String largeVariantId = "gid://shopify/ProductVariant/200";
        ShopifyGlobalCatalogResponse.Product selectionProduct = withSelected(
                rawProduct(productId, mediumVariantId, largeVariantId),
                List.of(new ShopifyGlobalCatalogResponse.SelectedOption("Size", "L"))
        );
        ShopifyGlobalCatalogProductResult detailResult = new ShopifyGlobalCatalogProductResult(
                successful(List.of()), selectionProduct, List.of());
        when(global.getProductWithDetails(any())).thenReturn(detailResult);
        CatalogProductReference anchor = new CatalogProductReference(
                "saved-product",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product(productId),
                variant(mediumVariantId),
                List.of(new ProductAttribute("variant-option", "Size", "M"))
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                anchor,
                new CatalogProductDetailSelection(
                        List.of(new ProductAttribute("variant-option", "Size", "L")),
                        List.of("Size")),
                new CatalogRehydrationContext("CZ", "cs"));

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().resolvedReference().externalVariantReference())
                .isEqualTo(variant(largeVariantId));
        assertThat(result.details().selected()).containsExactly(
                new com.meant.api.module.catalog.service.dto.RehydratedProductDetails.SelectedOption("Size", "L"));
        assertThat(result.details().selectedVariant().variantId()).isEqualTo(largeVariantId);
        ArgumentCaptor<ShopifyGlobalCatalogGetProductRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogGetProductRequest.class);
        verify(global).getProductWithDetails(request.capture());
        assertThat(request.getValue().id()).isEqualTo(productId);
        assertThat(request.getValue().selected()).containsExactly(
                new com.meant.api.provider.shopify.catalog.dto.ShopifyCatalogSelectedOption("Size", "L"));
        assertThat(request.getValue().preferences()).containsExactly("Size");
    }

    @Test
    void savedDetailMatchesExactRawVariantBeyondTheNormalizedCandidateCap() {
        ShopifyGlobalCatalogProvider global = providerSource(1);
        CatalogSourceResult cappedResult = successful(List.of(detailedCandidate(
                "product-1", "variant-m", "seller-a", "seller.example", "M", 1299)));
        ShopifyGlobalCatalogProductResult productResult = new ShopifyGlobalCatalogProductResult(
                cappedResult,
                rawProduct(),
                List.of()
        );
        when(global.getProductWithDetails(any())).thenReturn(productResult);
        CatalogProductReference requested = new CatalogProductReference(
                "saved-large",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-l"),
                List.of(new ProductAttribute("variant-option", "Size", "L"))
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                requested,
                new CatalogRehydrationContext("CZ", "cs")
        );

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().facts().price().minorUnits()).isEqualTo(1399L);
        assertThat(result.rehydration().resolvedReference().externalVariantReference())
                .isEqualTo(variant("variant-l"));
        assertThat(result.details().selectedVariant().variantId()).isEqualTo("variant-l");
    }

    @Test
    void rehydratesExactSiblingByLookingUpItsVariantIdentity() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        when(global.lookupCatalog(any())).thenAnswer(invocation -> {
            ShopifyGlobalCatalogLookupRequest request = invocation.getArgument(0);
            return request.ids().contains("variant-l")
                    ? successful(List.of(detailedCandidate(
                            "product-1", "variant-l", "seller-a", "seller.example", "L", 1399)))
                    : successful(List.of(detailedCandidate(
                            "product-1", "variant-m", "seller-a", "seller.example", "M", 1299)));
        });
        CatalogProductReference requested = new CatalogProductReference(
                "selected-large",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-l"),
                List.of(new ProductAttribute("variant-option", "Size", "L"))
        );

        CatalogProductRehydrationResult result = rehydrator(global, 50).rehydrate(
                List.of(requested),
                new CatalogRehydrationContext("CZ", "en")
        ).getFirst();

        assertThat(result.status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.resolvedReference().externalVariantReference()).isEqualTo(variant("variant-l"));
        assertThat(result.resolvedReference().selectedOptions()).containsExactly(
                new ProductAttribute("variant-option", "Size", "L"));
        assertThat(result.facts().price().minorUnits()).isEqualTo(1399L);
        ArgumentCaptor<ShopifyGlobalCatalogLookupRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogLookupRequest.class);
        verify(global).lookupCatalog(request.capture());
        assertThat(request.getValue().ids()).containsExactly("variant-l");
        verify(global, never()).getProductWithDetails(any());
    }

    @Test
    void rehydratesLaterProductThroughExactGetProductWhenLookupBatchWasGloballyCapped() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        CatalogSourceResult cappedLookup = successful(List.of(candidate(
                "product-1", "variant-1", "seller-a", 1000, available())));
        when(cappedLookup.truncated()).thenReturn(true);
        when(global.lookupCatalog(any())).thenReturn(cappedLookup);
        ShopifyGlobalCatalogResponse.Seller seller = new ShopifyGlobalCatalogResponse.Seller(
                "Seller B", "seller-b", "seller-b.example", "https://seller-b.example", List.of());
        ShopifyGlobalCatalogResponse.Variant laterVariant = rawVariant(
                "product-2", "variant-2", "default", "One size", "sku-2", 2200L, seller);
        ShopifyGlobalCatalogProductResult laterProductResult = new ShopifyGlobalCatalogProductResult(
                successful(List.of()),
                rawProduct("product-2", List.of(laterVariant)),
                List.of()
        );
        when(global.getProductWithDetails(any())).thenReturn(laterProductResult);
        ShopifyCatalogProductRehydrationProvider provider = rehydrator(global, 50);

        var results = provider.rehydrate(List.of(
                reference("first", "product-1", "variant-1", "seller-a", List.of()),
                reference("later", "product-2", "variant-2", "seller-b", List.of())
        ), new CatalogRehydrationContext("CZ", "en"));

        assertThat(results).extracting(CatalogProductRehydrationResult::status)
                .containsExactly(CatalogRehydrationStatus.FRESH, CatalogRehydrationStatus.FRESH);
        assertThat(results.getLast().facts().price().minorUnits()).isEqualTo(2200L);
        assertThat(results.getLast().resolvedReference().externalProductReference())
                .isEqualTo(product("product-2"));
        ArgumentCaptor<ShopifyGlobalCatalogGetProductRequest> request =
                ArgumentCaptor.forClass(ShopifyGlobalCatalogGetProductRequest.class);
        verify(global).getProductWithDetails(request.capture());
        assertThat(request.getValue().id()).isEqualTo("variant-2");
        assertThat(request.getValue().filters().available()).isFalse();
        assertThat(request.getValue().filters().shops()).containsExactly("seller-b");
    }

    @Test
    void savedDetailMatchesBundleWhenProviderReordersRawComponents() {
        ShopifyGlobalCatalogProvider global = providerSource(50);
        ShopifyGlobalCatalogResponse.Product base = rawProduct();
        ShopifyGlobalCatalogResponse.Variant configured = withComponents(
                base.variants().getFirst(),
                List.of(
                        new ShopifyGlobalCatalogResponse.Component(
                                "component-b", "component-variant-b", 2, List.of()),
                        new ShopifyGlobalCatalogResponse.Component(
                                "component-a", "component-variant-a", 1, List.of())
                )
        );
        ShopifyGlobalCatalogProductResult productResult = new ShopifyGlobalCatalogProductResult(
                successful(List.of()),
                withVariants(base, List.of(configured)),
                List.of()
        );
        when(global.getProductWithDetails(any())).thenReturn(productResult);
        List<OfferComponentIdentity> requestedComponents = List.of(
                new OfferComponentIdentity(
                        product("component-a"), variant("component-variant-a"), 1, List.of()),
                new OfferComponentIdentity(
                        product("component-b"), variant("component-variant-b"), 2, List.of())
        );
        CatalogProductReference requested = new CatalogProductReference(
                "saved-bundle",
                SOURCE,
                null,
                null,
                merchant("seller-a"),
                "seller.example",
                product("product-1"),
                variant("variant-m"),
                List.of(new ProductAttribute("variant-option", "Size", "M")),
                requestedComponents,
                null
        );

        CatalogProductDetailResult result = rehydrator(global, 50).getDetails(
                requested,
                new CatalogRehydrationContext("CZ", "cs")
        );

        assertThat(result.rehydration().status()).isEqualTo(CatalogRehydrationStatus.FRESH);
        assertThat(result.rehydration().resolvedReference().components()).containsExactlyElementsOf(requestedComponents);
        assertThat(result.details().selectedVariant().variantId()).isEqualTo("variant-m");
    }

    private ShopifyGlobalCatalogResponse.Product rawProduct() {
        return rawProduct("product-1", "variant-m", "variant-l");
    }

    private ShopifyGlobalCatalogResponse.Product rawProduct(
            String productId,
            String mediumVariantId,
            String largeVariantId
    ) {
        ShopifyGlobalCatalogResponse.Seller sellerA = new ShopifyGlobalCatalogResponse.Seller(
                "Seller A", "seller-a", "seller.example", "https://seller.example", List.of());
        ShopifyGlobalCatalogResponse.Seller sellerB = new ShopifyGlobalCatalogResponse.Seller(
                "Seller B", "seller-b", "other.example", "https://other.example", List.of());
        return new ShopifyGlobalCatalogResponse.Product(
                productId,
                "perfect-shirt",
                "Perfect shirt",
                new ShopifyGlobalCatalogResponse.Description(
                        "Full current product description", "<p>Full current product description</p>"),
                "https://seller.example/products/perfect-shirt",
                List.of(new ShopifyGlobalCatalogResponse.Category("Shirts", "apparel")),
                new ShopifyGlobalCatalogResponse.PriceRange(
                        new ShopifyGlobalCatalogResponse.Price(1299L, "USD"),
                        new ShopifyGlobalCatalogResponse.Price(1399L, "USD")),
                new ShopifyGlobalCatalogResponse.PriceRange(
                        new ShopifyGlobalCatalogResponse.Price(1599L, "USD"),
                        new ShopifyGlobalCatalogResponse.Price(1699L, "USD")),
                List.of(new ShopifyGlobalCatalogResponse.Media(
                        "image", "https://seller.example/product.jpg", "Perfect shirt", 1200, 1200)),
                List.of(new ShopifyGlobalCatalogResponse.Option(
                        "Size",
                        List.of(
                                new ShopifyGlobalCatalogResponse.OptionValue("M", true, true),
                                new ShopifyGlobalCatalogResponse.OptionValue("L", true, true)
                        ))),
                List.of(new ShopifyGlobalCatalogResponse.SelectedOption("Size", "M")),
                List.of(
                        rawVariant(productId, mediumVariantId, "medium", "M", "sku-medium", 1299L, sellerA),
                        rawVariant(productId, largeVariantId, "large", "L", "sku-large", 1399L, sellerA),
                        rawVariant(productId, mediumVariantId, "medium", "M", "other-sku", 999L, sellerB)
                ),
                new ShopifyGlobalCatalogResponse.Rating(new BigDecimal("4.7"), new BigDecimal("5"), 84L),
                List.of("organic", "summer"),
                new ShopifyGlobalCatalogResponse.Metadata(
                        List.of("180 gsm"), List.of("Machine washable"), List.of("Made for travel"))
        );
    }

    private ShopifyGlobalCatalogResponse.Product rawProduct(
            String productId,
            List<ShopifyGlobalCatalogResponse.Variant> variants
    ) {
        return new ShopifyGlobalCatalogResponse.Product(
                productId,
                productId,
                "Current " + productId,
                null,
                "https://seller-b.example/products/" + productId,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                variants,
                null,
                List.of(),
                null
        );
    }

    private ShopifyGlobalCatalogResponse.Variant rawVariant(
            String variantId,
            String handle,
            String size,
            String sku,
            long price,
            ShopifyGlobalCatalogResponse.Seller seller
    ) {
        return rawVariant("product-1", variantId, handle, size, sku, price, seller);
    }

    private ShopifyGlobalCatalogResponse.Variant rawVariant(
            String productId,
            String variantId,
            String handle,
            String size,
            String sku,
            long price,
            ShopifyGlobalCatalogResponse.Seller seller
    ) {
        return new ShopifyGlobalCatalogResponse.Variant(
                variantId,
                productId,
                sku,
                handle,
                "Size " + size,
                new ShopifyGlobalCatalogResponse.Description(
                        ("M".equals(size) ? "Medium" : "Large") + " variant description", null),
                "https://" + seller.domain() + "/products/perfect-shirt?variant=" + variantId,
                new ShopifyGlobalCatalogResponse.Price(price, "USD"),
                new ShopifyGlobalCatalogResponse.Price(price + 300, "USD"),
                new ShopifyGlobalCatalogResponse.Availability(true, "in_stock", 10, false),
                new ShopifyGlobalCatalogResponse.Requires(true, false, false),
                List.of(new ShopifyGlobalCatalogResponse.SelectedOption("Size", size)),
                List.of(new ShopifyGlobalCatalogResponse.Media(
                        "image", "https://" + seller.domain() + "/" + variantId + ".jpg",
                        "Shirt " + size, 1200, 1200)),
                List.of(new ShopifyGlobalCatalogResponse.Category("Shirts", "apparel")),
                List.of("organic"),
                List.of(new ShopifyGlobalCatalogResponse.Barcode("SKU", sku)),
                List.of(),
                seller,
                "https://" + seller.domain() + "/cart/" + variantId + ":1",
                null,
                List.of()
        );
    }

    private ShopifyGlobalCatalogResponse.Variant withComponents(
            ShopifyGlobalCatalogResponse.Variant variant,
            List<ShopifyGlobalCatalogResponse.Component> components
    ) {
        return new ShopifyGlobalCatalogResponse.Variant(
                variant.id(),
                variant.productId(),
                variant.sku(),
                variant.handle(),
                variant.title(),
                variant.description(),
                variant.url(),
                variant.price(),
                variant.listPrice(),
                variant.availability(),
                variant.requires(),
                variant.options(),
                variant.media(),
                variant.categories(),
                variant.tags(),
                variant.barcodes(),
                variant.inputs(),
                variant.seller(),
                variant.checkoutUrl(),
                variant.sellingPlan(),
                components
        );
    }

    private ShopifyGlobalCatalogResponse.Variant withOptions(
            ShopifyGlobalCatalogResponse.Variant variant,
            List<ShopifyGlobalCatalogResponse.SelectedOption> options
    ) {
        return new ShopifyGlobalCatalogResponse.Variant(
                variant.id(),
                variant.productId(),
                variant.sku(),
                variant.handle(),
                variant.title(),
                variant.description(),
                variant.url(),
                variant.price(),
                variant.listPrice(),
                variant.availability(),
                variant.requires(),
                options,
                variant.media(),
                variant.categories(),
                variant.tags(),
                variant.barcodes(),
                variant.inputs(),
                variant.seller(),
                variant.checkoutUrl(),
                variant.sellingPlan(),
                variant.components()
        );
    }

    private ShopifyGlobalCatalogResponse.Variant withSeller(
            ShopifyGlobalCatalogResponse.Variant variant,
            ShopifyGlobalCatalogResponse.Seller seller
    ) {
        return new ShopifyGlobalCatalogResponse.Variant(
                variant.id(),
                variant.productId(),
                variant.sku(),
                variant.handle(),
                variant.title(),
                variant.description(),
                variant.url(),
                variant.price(),
                variant.listPrice(),
                variant.availability(),
                variant.requires(),
                variant.options(),
                variant.media(),
                variant.categories(),
                variant.tags(),
                variant.barcodes(),
                variant.inputs(),
                seller,
                variant.checkoutUrl(),
                variant.sellingPlan(),
                variant.components()
        );
    }

    private ShopifyGlobalCatalogResponse.Product withVariants(
            ShopifyGlobalCatalogResponse.Product product,
            List<ShopifyGlobalCatalogResponse.Variant> variants
    ) {
        return new ShopifyGlobalCatalogResponse.Product(
                product.id(),
                product.handle(),
                product.title(),
                product.description(),
                product.url(),
                product.categories(),
                product.priceRange(),
                product.listPriceRange(),
                product.media(),
                product.options(),
                product.selected(),
                variants,
                product.rating(),
                product.tags(),
                product.metadata()
        );
    }

    private ShopifyGlobalCatalogResponse.Product withSelected(
            ShopifyGlobalCatalogResponse.Product product,
            List<ShopifyGlobalCatalogResponse.SelectedOption> selected
    ) {
        return new ShopifyGlobalCatalogResponse.Product(
                product.id(),
                product.handle(),
                product.title(),
                product.description(),
                product.url(),
                product.categories(),
                product.priceRange(),
                product.listPriceRange(),
                product.media(),
                product.options(),
                selected,
                product.variants(),
                product.rating(),
                product.tags(),
                product.metadata()
        );
    }

    private ShopifyCatalogProductRehydrationProvider rehydrator(
            ShopifyGlobalCatalogProvider provider,
            int maximumLookupIds
    ) {
        ShopifyGlobalCatalogProperties properties = mock(ShopifyGlobalCatalogProperties.class);
        when(properties.maximumLookupIds()).thenReturn(maximumLookupIds);
        when(properties.sourceIdentity()).thenReturn("SHOPIFY_GLOBAL_CATALOG");
        when(properties.endpoint()).thenReturn(URI.create("https://catalog.test"));
        return new ShopifyCatalogProductRehydrationProvider(
                provider,
                properties,
                new ShopifyCatalogDataUseProperties(false, Duration.ofMinutes(15), Duration.ofMinutes(2)),
                new ShopifyCatalogReferenceMatcher(),
                new ShopifyGlobalCatalogNormalizer(properties, Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ShopifyGlobalCatalogProvider providerSource(int ignored) {
        ShopifyGlobalCatalogProvider provider = mock(ShopifyGlobalCatalogProvider.class);
        when(provider.discoverySourceIdentity()).thenReturn(SOURCE);
        return provider;
    }

    private CatalogSourceResult successful(List<ProductCandidate> candidates) {
        CatalogSourceResult result = mock(CatalogSourceResult.class);
        when(result.successful()).thenReturn(true);
        when(result.candidates()).thenReturn(candidates);
        return result;
    }

    private OfferAvailability available() {
        return new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null);
    }

    private CatalogSourceResult throwFailure() {
        throw new IllegalStateException("failed chunk");
    }

    private ProductCandidate candidate(
            String productId,
            String variantId,
            String seller,
            long price,
            OfferAvailability availability
    ) {
        return candidate(productId, variantId, seller, null, price, availability);
    }

    private ProductCandidate detailedCandidate(
            String productId,
            String variantId,
            String seller,
            String merchantDomain,
            String size,
            long price
    ) {
        ExternalIdentifier merchant = merchant(seller);
        ExternalIdentifier product = product(productId);
        ExternalIdentifier variant = variant(variantId);
        ResultSourceReference sourceReference = new ResultSourceReference(
                ResultSourceType.PROVIDER_CATALOG, "test", URI.create("https://catalog.test"));
        ResultProvenance provenance = new ResultProvenance(
                SHOPIFY,
                SOURCE,
                null,
                merchant,
                merchantDomain,
                product,
                variant,
                new ResultFreshness(NOW, null),
                sourceReference
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        SHOPIFY,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(new ProductAttribute("variant-option", "Size", size)),
                        List.of(),
                        null
                ),
                seller,
                "Size " + size,
                new Money(price, "USD"),
                new Money(price + 300, "USD"),
                available(),
                List.of(),
                URI.create("https://seller.example/cart/" + variantId),
                List.of(provenance)
        );
        return new ProductCandidate(
                "Perfect shirt",
                "Full description for " + variantId,
                List.of(new ProductMedia(
                        ProductMediaType.IMAGE,
                        URI.create("https://seller.example/" + variantId + ".jpg"),
                        "Shirt " + size,
                        1200,
                        1200
                )),
                List.of(
                        new ProductAttribute("category", "apparel", "Shirts"),
                        new ProductAttribute("technical specification", "Weight", "180 gsm")
                ),
                List.of(new ProductMaterial("Organic cotton", 10_000)),
                List.of(new ProductCertification("GOTS", null, null, null)),
                List.of(new ProductAttribution(
                        "Product",
                        URI.create("https://seller.example/products/" + productId),
                        sourceReference
                )),
                List.of(),
                List.of(provenance),
                offer
        );
    }

    private ProductCandidate candidate(
            String productId,
            String variantId,
            String seller,
            String merchantDomain,
            long price,
            OfferAvailability availability
    ) {
        ExternalIdentifier merchant = merchant(seller);
        ExternalIdentifier product = product(productId);
        ExternalIdentifier variant = variant(variantId);
        ResultProvenance provenance = new ResultProvenance(
                SHOPIFY,
                SOURCE,
                null,
                merchant,
                merchantDomain,
                product,
                variant,
                new ResultFreshness(NOW, null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "test", URI.create("https://catalog.test"))
        );
        Offer offer = new Offer(
                new OfferIdentity(
                        SHOPIFY,
                        OfferMerchantScope.external(merchant),
                        product,
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                seller,
                variantId,
                new Money(price, "USD"),
                null,
                availability,
                List.of(),
                null,
                List.of(provenance)
        );
        return new ProductCandidate(
                "Current product",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(provenance),
                offer
        );
    }

    private CatalogProductReference reference(
            String key,
            String product,
            String variant,
            String merchant,
            List<ProductAttribute> options
    ) {
        return new CatalogProductReference(
                key,
                SOURCE,
                null,
                null,
                merchant(merchant),
                product(product),
                variant(variant),
                options
        );
    }

    private ExternalIdentifier merchant(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "SHOPIFY", value);
    }

    private ExternalIdentifier product(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "SHOPIFY", value);
    }

    private ExternalIdentifier variant(String value) {
        return new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", value);
    }
}

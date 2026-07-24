package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.merchant.constant.MerchantCatalogSourceIdentity;
import com.meant.api.module.merchant.service.MerchantPresentationOriginService;
import com.meant.api.module.catalog.service.CatalogPurchaseReferencePolicyResolver;
import com.meant.api.module.user.entity.UserSavedProduct;
import com.meant.api.module.user.service.dto.UserSavedProductResult;
import com.meant.api.module.catalog.service.dto.CatalogProductDetailResult;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.Money;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.RehydratedProductDetails;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.provider.shopify.catalog.ShopifyCatalogPurchaseReferencePolicy;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserSavedProductResultMapperTest {
    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private final MerchantPresentationOriginService merchantOriginService =
            mock(MerchantPresentationOriginService.class);
    private final UserSavedProductResultMapper mapper = new UserSavedProductResultMapper(
            new ObjectMapper(),
            new CatalogPurchaseReferencePolicyResolver(List.of(new ShopifyCatalogPurchaseReferencePolicy())),
            merchantOriginService
    );

    @Test
    void preservesIsoCurrencyExponentAndExactMinorUnitsForFreshOffers() {
        assertMoney("USD", 1234, 12.34d);
        assertMoney("EUR", 1234, 12.34d);
        assertMoney("JPY", 1234, 1234.0d);
        assertMoney("KWD", 1234, 1.234d);
    }

    @Test
    void invalidOrUnrepresentableCurrencyKeepsExactOfferIdentityWithoutPublishingAPrice() {
        for (String currency : List.of("ZZZ", "XXX")) {
            UserSavedProductResult result = result(currency, 1234);

            assertThat(result.priceFrom()).isNull();
            assertThat(result.priceFromMinorUnits()).isNull();
            assertThat(result.priceCurrency()).isNull();
            assertThat(result.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.price()).isNull();
                assertThat(offer.priceMinorUnits()).isNull();
                assertThat(offer.priceCurrency()).isNull();
            });
            assertThat(result.commercialFactsAuthoritative()).isTrue();
        }
    }

    @Test
    void savedProductListUsesCurrentMerchantNameInsteadOfInternalRoutingIdentity() {
        java.util.UUID integrationId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000012");
        java.util.UUID merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000013");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("GENERIC_UCP"),
                ResultSourceType.MERCHANT_STOREFRONT,
                "LOCAL_STOREFRONT:" + integrationId
        );
        CatalogProductReference requested = new CatalogProductReference(
                "product-local",
                source,
                null,
                new LocalMerchantRouting(integrationId),
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-local"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-local"),
                List.of()
        );
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                source,
                merchantId,
                requested.localRouting(),
                null,
                requested.externalProductReference(),
                requested.externalVariantReference(),
                List.of()
        );
        ResultFreshness freshness = new ResultFreshness(NOW, NOW.plusSeconds(120));
        UserSavedProduct entity = entity(requested);

        UserSavedProductResult result = mapper.result(
                entity,
                CatalogProductRehydrationResult.fresh(
                        requested,
                        resolved,
                        new RehydratedCommercialFacts(
                                "Current local product",
                                "Human Merchant",
                                new Money(1299, "USD"),
                                new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                                resolved.externalVariantReference(),
                                List.of(),
                                List.of(),
                                List.of(),
                                freshness,
                                CommercialFactsFreshness.fromSingleObservation(freshness)
                        )
                ),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.merchant()).isEqualTo("Human Merchant");
            assertThat(offer.merchantId()).isEqualTo(merchantId.toString());
            SavedProductOfferKeyCodec.Selection selection = SavedProductOfferKeyCodec.decode(offer.offerKey())
                    .orElseThrow();
            assertThat(selection.savedProductId()).isEqualTo(entity.getId());
            assertThat(SavedProductOfferKeyCodec.verify(selection, entity)).isTrue();
        });
    }

    @Test
    void savedProductDetailPrefersDetailMerchantNameOverFactsDomainAndInternalIdentifiers() {
        CatalogProductReference requested = reference();
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                requested.discoverySource(),
                requested.localMerchantId(),
                requested.localRouting(),
                requested.externalMerchantReference(),
                "merchant.example",
                requested.externalProductReference(),
                requested.externalVariantReference(),
                requested.selectedOptions()
        );
        CatalogProductRehydrationResult rehydrated = fresh(
                requested,
                resolved,
                List.of(),
                "Facts Merchant"
        );

        UserSavedProductResult result = mapper.detailResult(
                entity(requested),
                CatalogProductDetailResult.from(rehydrated, details("Detail Merchant")),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.offers()).singleElement()
                .extracting(UserSavedProductResult.Offer::merchant)
                .isEqualTo("Detail Merchant");
        assertThat(result.details().merchantName()).isEqualTo("Detail Merchant");
    }

    @Test
    void legacyGenericReferenceKeepsVerifiedRoutingWithoutUsingItsDomainAsAMerchantLabel() {
        when(merchantOriginService.resolve(any(CatalogProductReference.class)))
                .thenReturn("merchant.example");
        java.util.UUID integrationId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000022");
        java.util.UUID merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000023");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("GENERIC_UCP"),
                ResultSourceType.MERCHANT_STOREFRONT,
                "MEANT_MERCHANT_SEMANTIC"
        );
        CatalogProductReference requested = new CatalogProductReference(
                "legacy-generic-product",
                source,
                merchantId,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-local"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-local"),
                List.of()
        );
        List<ProductAttribute> resolvedOptions = List.of(new ProductAttribute("variant", "Size", "Large"));
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                source,
                merchantId,
                new LocalMerchantRouting(integrationId),
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-local"),
                "merchant.example",
                requested.externalProductReference(),
                requested.externalVariantReference(),
                resolvedOptions
        );
        UserSavedProduct entity = entity(requested);

        UserSavedProductResult result = mapper.result(
                entity,
                fresh(requested, resolved, resolvedOptions),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.commercialFactsAuthoritative()).isTrue();
        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.merchant()).isEqualTo("Merchant");
            assertThat(offer.merchantId()).isEqualTo(merchantId.toString());
            assertThat(offer.merchantOrigin()).isEqualTo("merchant.example");
            assertThat(SavedProductOfferKeyCodec.verify(
                    SavedProductOfferKeyCodec.decode(offer.offerKey()).orElseThrow(), entity)).isTrue();
        });
    }

    @Test
    void freshLegacyOfferWithoutExecutableMerchantRouteDoesNotIssueACartKey() {
        java.util.UUID merchantId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000033");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("GENERIC_UCP"),
                ResultSourceType.MERCHANT_STOREFRONT,
                "LEGACY_LOCAL_MERCHANT"
        );
        CatalogProductReference reference = new CatalogProductReference(
                "legacy-unroutable-product",
                source,
                merchantId,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-local"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-local"),
                List.of()
        );

        UserSavedProductResult result = mapper.result(
                entity(reference),
                fresh(reference, reference, List.of()),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.commercialFactsAuthoritative()).isTrue();
        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.merchant()).isEqualTo("Merchant");
            assertThat(offer.available()).isTrue();
            assertThat(offer.offerKey()).isNull();
        });
    }

    @Test
    void externalShopifyOfferWithoutVerifiedDomainDoesNotIssueACartKey() {
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                new ProviderIdentity("SHOPIFY"),
                ResultSourceType.PROVIDER_CATALOG,
                "SHOPIFY_GLOBAL_CATALOG"
        );
        CatalogProductReference reference = new CatalogProductReference(
                "shopify-unroutable-product",
                source,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "SHOPIFY", "merchant-1"),
                null,
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "SHOPIFY", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "SHOPIFY", "variant-1"),
                List.of()
        );

        UserSavedProductResult result = mapper.result(
                entity(reference),
                fresh(reference, reference, List.of()),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.commercialFactsAuthoritative()).isTrue();
        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.available()).isTrue();
            assertThat(offer.offerKey()).isNull();
        });
    }

    @Test
    void legacyEmptyOptionsStillRequiresFactsToMatchResolvedOptions() {
        CatalogProductReference requested = reference();
        List<ProductAttribute> resolvedOptions = List.of(new ProductAttribute("variant", "Size", "Large"));
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                requested.discoverySource(),
                requested.localMerchantId(),
                requested.localRouting(),
                requested.externalMerchantReference(),
                requested.externalMerchantDomain(),
                requested.externalProductReference(),
                requested.externalVariantReference(),
                resolvedOptions
        );

        UserSavedProductResult result = mapper.result(
                entity(requested),
                fresh(
                        requested,
                        resolved,
                        List.of(new ProductAttribute("variant", "Size", "Medium"))
                ),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.commercialFactsAuthoritative()).isFalse();
        assertThat(result.offers()).isEmpty();
    }

    @Test
    void nonEmptyStoredOptionsMustStillMatchTheResolvedReference() {
        CatalogProductReference base = reference();
        CatalogProductReference requested = new CatalogProductReference(
                base.interactionKey(),
                base.discoverySource(),
                base.localMerchantId(),
                base.localRouting(),
                base.externalMerchantReference(),
                base.externalMerchantDomain(),
                base.externalProductReference(),
                base.externalVariantReference(),
                List.of(new ProductAttribute("variant", "Size", "Large"))
        );
        List<ProductAttribute> resolvedOptions = List.of(new ProductAttribute("variant", "Size", "Medium"));
        CatalogProductReference resolved = new CatalogProductReference(
                requested.interactionKey(),
                requested.discoverySource(),
                requested.localMerchantId(),
                requested.localRouting(),
                requested.externalMerchantReference(),
                requested.externalMerchantDomain(),
                requested.externalProductReference(),
                requested.externalVariantReference(),
                resolvedOptions
        );

        UserSavedProductResult result = mapper.result(
                entity(requested),
                fresh(requested, resolved, resolvedOptions),
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.commercialFactsAuthoritative()).isFalse();
        assertThat(result.offers()).isEmpty();
    }

    @Test
    void unavailableProviderKeepsPresentationSnapshotWithoutCommercialAuthority() {
        UserSavedProductResult result = mapper.result(entity(), null, null);

        assertThat(result.name()).isEqualTo("Saved title");
        assertThat(result.imageUrl()).isEqualTo("https://saved.test/image.jpg");
        assertThat(result.priceFrom()).isNull();
        assertThat(result.offers()).isEmpty();
        assertThat(result.commercialFactsAuthoritative()).isFalse();
    }

    @Test
    void soldOutSavedVariantKeepsASelectionAnchorForChoosingASibling() {
        CatalogProductReference reference = reference();
        ResultFreshness freshness = new ResultFreshness(NOW, NOW.plusSeconds(120));
        UserSavedProduct entity = entity(reference);
        CatalogProductRehydrationResult rehydrated = CatalogProductRehydrationResult.fresh(
                reference,
                reference,
                new RehydratedCommercialFacts(
                        "Current product",
                        new Money(1299, "USD"),
                        new OfferAvailability(OfferAvailabilityStatus.OUT_OF_STOCK, 0, null),
                        reference.externalVariantReference(),
                        List.of(),
                        List.of(),
                        List.of(),
                        freshness,
                        CommercialFactsFreshness.fromSingleObservation(freshness)
                )
        );

        UserSavedProductResult result = mapper.result(
                entity,
                rehydrated,
                new CatalogRehydrationContext("CZ", null)
        );

        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.available()).isFalse();
            assertThat(SavedProductOfferKeyCodec.verify(
                    SavedProductOfferKeyCodec.decode(offer.offerKey()).orElseThrow(), entity)).isTrue();
        });
    }

    private void assertMoney(String currency, long minorUnits, double majorUnits) {
        UserSavedProductResult result = result(currency, minorUnits);

        assertThat(result.priceFrom()).isEqualTo(majorUnits);
        assertThat(result.priceFromMinorUnits()).isEqualTo(minorUnits);
        assertThat(result.priceCurrency()).isEqualTo(currency);
        assertThat(result.commercialFactsAuthoritative()).isTrue();
        assertThat(result.offers()).singleElement().satisfies(offer -> {
            assertThat(offer.price()).isEqualTo(majorUnits);
            assertThat(offer.priceMinorUnits()).isEqualTo(minorUnits);
            assertThat(offer.priceCurrency()).isEqualTo(currency);
            assertThat(offer.delivery()).isNull();
        });
    }

    private UserSavedProductResult result(String currency, long minorUnits) {
        CatalogProductReference reference = reference();
        ResultFreshness freshness = new ResultFreshness(NOW, NOW.plusSeconds(120));
        CatalogProductRehydrationResult rehydrated = CatalogProductRehydrationResult.fresh(
                reference,
                reference,
                new RehydratedCommercialFacts(
                        "Current product",
                        new Money(minorUnits, currency),
                        OfferAvailability.unknown(),
                        reference.externalVariantReference(),
                        List.of(),
                        List.of(),
                        List.of(),
                        freshness,
                        CommercialFactsFreshness.fromSingleObservation(freshness)
                )
        );
        return mapper.result(entity(), rehydrated, new CatalogRehydrationContext("CZ", null));
    }

    private CatalogProductRehydrationResult fresh(
            CatalogProductReference requested,
            CatalogProductReference resolved,
            List<ProductAttribute> factOptions
    ) {
        return fresh(requested, resolved, factOptions, null);
    }

    private CatalogProductRehydrationResult fresh(
            CatalogProductReference requested,
            CatalogProductReference resolved,
            List<ProductAttribute> factOptions,
            String merchantName
    ) {
        ResultFreshness freshness = new ResultFreshness(NOW, NOW.plusSeconds(120));
        return CatalogProductRehydrationResult.fresh(
                requested,
                resolved,
                new RehydratedCommercialFacts(
                        "Current product",
                        merchantName,
                        new Money(1299, "USD"),
                        new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, null, null),
                        resolved.externalVariantReference(),
                        factOptions,
                        List.of(),
                        List.of(),
                        freshness,
                        CommercialFactsFreshness.fromSingleObservation(freshness)
                )
        );
    }

    private RehydratedProductDetails details(String merchantName) {
        return new RehydratedProductDetails(
                "product-1",
                null,
                "Current product",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                null,
                null,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                merchantName
        );
    }

    private UserSavedProduct entity() {
        return UserSavedProduct.create(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "product-1",
                new UserSavedProduct.DurableReferenceSnapshot(
                        "GENERIC_UCP",
                        "MERCHANT_STOREFRONT",
                        "MEANT_MERCHANT_SEMANTIC",
                        null,
                        null,
                        "merchant-1",
                        null,
                        "product-1",
                        "variant-1",
                        "[]",
                        "generic-ucp-storefront-v2"
                ),
                new UserSavedProduct.PresentationSnapshot(
                        "hash",
                        "Saved title",
                        "Saved brand",
                        "Saved category",
                        "#fff",
                        "https://saved.test/image.jpg",
                        "https://saved.test/product",
                        true,
                        80,
                        2,
                        "[]",
                        "[]",
                        "Saved note",
                        "[]",
                        "[]",
                        4.5d,
                        10,
                        "Saved review",
                        null,
                        "[]"
                ),
                NOW
        );
    }

    private UserSavedProduct entity(CatalogProductReference reference) {
        return UserSavedProduct.create(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                reference.interactionKey(),
                new UserSavedProduct.DurableReferenceSnapshot(
                        reference.discoverySource().provider().value(),
                        reference.discoverySource().type().name(),
                        reference.discoverySource().value(),
                        reference.localMerchantId(),
                        reference.localRouting() == null
                                ? null
                                : reference.localRouting().merchantIntegrationId(),
                        reference.externalMerchantReference() == null
                                ? null
                                : reference.externalMerchantReference().value(),
                        reference.externalMerchantDomain(),
                        reference.externalProductReference().value(),
                        reference.externalVariantReference() == null
                                ? null
                                : reference.externalVariantReference().value(),
                        "[]",
                        "generic-ucp-storefront-v2"
                ),
                new UserSavedProduct.PresentationSnapshot(
                        "hash", "Saved title", "Saved brand", "Saved category", "#fff",
                        "https://saved.test/image.jpg", "https://saved.test/product", true, 80, 1,
                        "[]", "[]", "Saved note", "[]", "[]", 4.5d, 10, "Saved review", null, "[]"
                ),
                NOW
        );
    }

    private CatalogProductReference reference() {
        return new CatalogProductReference(
                "product-1",
                MerchantCatalogSourceIdentity.DISCOVERY_SOURCE,
                null,
                null,
                new ExternalIdentifier(ExternalIdentifierType.MERCHANT, "GENERIC_UCP", "merchant-1"),
                new ExternalIdentifier(ExternalIdentifierType.PRODUCT, "GENERIC_UCP", "product-1"),
                new ExternalIdentifier(ExternalIdentifierType.VARIANT, "GENERIC_UCP", "variant-1"),
                List.of()
        );
    }
}

package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.catalog.service.dto.CanonicalProduct;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryFilters;
import com.meant.api.module.catalog.service.dto.CatalogDiscoveryPrice;
import com.meant.api.module.catalog.service.dto.CatalogSimilarityReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.IdentityEvidenceStrength;
import com.meant.api.module.catalog.service.dto.Offer;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidence;
import com.meant.api.module.catalog.service.dto.ProductIdentityEvidenceKind;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.user.exception.UserException;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchSimilarUserProductsCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserQualifiedProductSearchInput;
import com.meant.api.provider.shopify.catalog.ShopifyCatalogSimilarityReferenceResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSimilarProductSearchServiceTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID QUALIFICATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID CONVERSATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID MERCHANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final ProviderIdentity PROVIDER = new ProviderIdentity("GENERIC_UCP");
    private static final ProviderIdentity SHOPIFY = new ProviderIdentity("SHOPIFY");
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-18T08:00:00Z");

    @Test
    void prefersUpidEvidenceAndRetypesItAsAProductReferenceWithoutUsingSyntheticOfferIdentity() {
        UserCanonicalProductSessionStore store = store();
        CanonicalProduct anchor = product(true, false, true);
        store.remember(USER_ID, List.of(anchor), Map.of(), Map.of(), Map.of(), List.of());
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store,
                persistence(Optional.empty()),
                grouped,
                new ShopifyCatalogSimilarityReferenceResolver(),
                null
        );

        var result = service.search(profile(USER_ID), command(USER_ID, anchor.key()));

        assertThat(result).isSameAs(grouped.result);
        assertThat(grouped.reference.provider()).isEqualTo(SHOPIFY);
        assertThat(grouped.reference.productReference()).satisfies(reference -> {
            assertThat(reference.type()).isEqualTo(ExternalIdentifierType.PRODUCT);
            assertThat(reference.namespace()).isEqualTo(SHOPIFY.value());
            assertThat(reference.value()).isEqualTo("gid://shopify/p/universal-product");
            assertThat(reference.value()).doesNotStartWith("variant-product:v1:");
        });
        assertThat(grouped.anchor).isSameAs(anchor);
        assertThat(grouped.command.query()).isEqualTo("trail running shoes");
        assertThat(grouped.command.offset()).isEqualTo(0);
        assertThat(grouped.command.limit()).isEqualTo(20);
    }

    @Test
    void restoresDurableIdentifiersAndPrefersProviderCatalogProductProvenance() {
        CanonicalProduct durableAnchor = product(false, true, true);
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store(),
                persistence(Optional.of(durableAnchor)),
                grouped,
                new ShopifyCatalogSimilarityReferenceResolver(),
                null
        );

        service.search(profile(USER_ID), command(USER_ID, durableAnchor.key()));

        assertThat(grouped.reference.provider()).isEqualTo(SHOPIFY);
        assertThat(grouped.reference.productReference().value())
                .isEqualTo("gid://shopify/Product/provider-catalog-product");
    }

    @Test
    void targetsShopifyGlobalCatalogForAMerchantScopedProductWithAShopifyProductGid() {
        UserCanonicalProductSessionStore store = store();
        CanonicalProduct anchor = product(false, true, false);
        store.remember(USER_ID, List.of(anchor), Map.of(), Map.of(), Map.of(), List.of());
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store,
                persistence(Optional.empty()),
                grouped,
                new ShopifyCatalogSimilarityReferenceResolver(),
                null
        );

        service.search(profile(USER_ID), command(USER_ID, anchor.key()));

        assertThat(grouped.reference.provider()).isEqualTo(SHOPIFY);
        assertThat(grouped.reference.productReference().value())
                .isEqualTo("gid://shopify/Product/storefront-product");
        assertThat(grouped.command.merchantId()).isNull();
    }

    @Test
    void appliesTypedFiltersFromTheOriginatingQualification() {
        UserCanonicalProductSessionStore store = store();
        CanonicalProduct anchor = product(true, false, true);
        store.remember(USER_ID, List.of(anchor), Map.of(), Map.of(), Map.of(), List.of());
        CatalogDiscoveryFilters filters = new CatalogDiscoveryFilters(
                true,
                List.of(),
                null,
                List.of(),
                new CatalogDiscoveryPrice(null, 5_000L),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        );
        UserQualifiedProductSearchResolver resolver = new UserQualifiedProductSearchResolver(null, null, null) {
            @Override
            public UserQualifiedProductSearchInput resolve(UUID userId, UUID qualificationId) {
                assertThat(userId).isEqualTo(USER_ID);
                assertThat(qualificationId).isEqualTo(QUALIFICATION_ID);
                return new UserQualifiedProductSearchInput(
                        QUALIFICATION_ID,
                        CONVERSATION_ID,
                        null,
                        "blue jeans",
                        filters
                );
            }
        };
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store,
                persistence(Optional.empty()),
                grouped,
                new ShopifyCatalogSimilarityReferenceResolver(),
                resolver
        );

        service.search(
                profile(USER_ID),
                new SearchSimilarUserProductsCommand(
                        USER_ID,
                        anchor.key(),
                        "blue jeans",
                        QUALIFICATION_ID,
                        "192.0.2.10",
                        "test-agent"
                )
        );

        assertThat(grouped.command.query()).isEqualTo("blue jeans");
        assertThat(grouped.discoveryFilters).isSameAs(filters);
        assertThat(grouped.discoveryFilters.price().max()).isEqualTo(5_000L);
    }

    @Test
    void keepsMerchantScopedSimilarityOnTheDirectStorefrontSource() {
        UserCanonicalProductSessionStore store = store();
        CanonicalProduct anchor = product(false, true, false);
        store.remember(USER_ID, List.of(anchor), Map.of(), Map.of(), Map.of(), List.of());
        UserQualifiedProductSearchResolver resolver = new UserQualifiedProductSearchResolver(null, null, null) {
            @Override
            public UserQualifiedProductSearchInput resolve(UUID userId, UUID qualificationId) {
                assertThat(userId).isEqualTo(USER_ID);
                assertThat(qualificationId).isEqualTo(QUALIFICATION_ID);
                return new UserQualifiedProductSearchInput(
                        QUALIFICATION_ID,
                        CONVERSATION_ID,
                        MERCHANT_ID,
                        "blue jeans",
                        null
                );
            }
        };
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store,
                persistence(Optional.empty()),
                grouped,
                product -> {
                    throw new AssertionError("Scoped similarity must not resolve a provider-catalog reference");
                },
                resolver
        );

        service.search(
                profile(USER_ID),
                new SearchSimilarUserProductsCommand(
                        USER_ID,
                        anchor.key(),
                        "blue jeans",
                        QUALIFICATION_ID,
                        "192.0.2.10",
                        "test-agent"
                )
        );

        assertThat(grouped.command.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(grouped.command.query()).isEqualTo("blue jeans");
        assertThat(grouped.reference).isNull();
    }

    @Test
    void acceptsAServerOwnedMerchantScopeWithoutAQualification() {
        UserCanonicalProductSessionStore store = store();
        CanonicalProduct anchor = product(false, true, false);
        store.remember(USER_ID, List.of(anchor), Map.of(), Map.of(), Map.of(), List.of());
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store,
                persistence(Optional.empty()),
                grouped,
                product -> {
                    throw new AssertionError("Direct merchant similarity must not resolve a global reference");
                },
                null
        );

        service.search(
                profile(USER_ID),
                new SearchSimilarUserProductsCommand(
                        USER_ID,
                        anchor.key(),
                        "blue jeans",
                        null,
                        MERCHANT_ID,
                        "192.0.2.10",
                        "test-agent"
                )
        );

        assertThat(grouped.command.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(grouped.command.query()).isEqualTo("blue jeans");
        assertThat(grouped.discoveryFilters).isNull();
        assertThat(grouped.reference).isNull();
    }

    @Test
    void rejectsAUserMismatchAndUnknownCanonicalKeyBeforeSimilarityDiscovery() {
        CapturingGroupedProductSearchService grouped = new CapturingGroupedProductSearchService();
        UserSimilarProductSearchService service = new UserSimilarProductSearchService(
                store(),
                persistence(Optional.empty()),
                grouped,
                new ShopifyCatalogSimilarityReferenceResolver(),
                null
        );

        assertThatThrownBy(() -> service.search(profile(OTHER_USER_ID), command(USER_ID, "unknown")))
                .isInstanceOf(UserException.class)
                .satisfies(exception -> assertThat(((UserException) exception).getStatus().value()).isEqualTo(403));
        assertThatThrownBy(() -> service.search(profile(USER_ID), command(USER_ID, "unknown")))
                .isInstanceOf(UserException.class)
                .satisfies(exception -> assertThat(((UserException) exception).getStatus().value()).isEqualTo(404));
        assertThat(grouped.calls).isZero();
    }

    private CanonicalProduct product(boolean withUpid, boolean storefrontFirst, boolean includeProviderCatalog) {
        ResultProvenance storefront = provenance(
                ResultSourceType.MERCHANT_STOREFRONT,
                "STOREFRONT",
                "gid://shopify/Product/storefront-product"
        );
        ResultProvenance providerCatalog = provenance(
                ResultSourceType.PROVIDER_CATALOG,
                "GLOBAL_CATALOG",
                "gid://shopify/Product/provider-catalog-product"
        );
        List<ResultProvenance> provenance = includeProviderCatalog
                ? storefrontFirst
                        ? List.of(storefront, providerCatalog)
                        : List.of(providerCatalog, storefront)
                : List.of(storefront);
        ExternalIdentifier variant = identifier(
                ExternalIdentifierType.VARIANT, "gid://shopify/ProductVariant/reference-variant");
        Offer offer = new Offer(
                new OfferIdentity(
                        PROVIDER,
                        OfferMerchantScope.external(identifier(ExternalIdentifierType.MERCHANT, "merchant")),
                        identifier(
                                ExternalIdentifierType.PRODUCT,
                                "variant-product:v1:gid://shopify/ProductVariant/reference-variant"
                        ),
                        variant,
                        List.of(),
                        List.of(),
                        null
                ),
                "Merchant",
                null,
                null,
                null,
                OfferAvailability.unknown(),
                List.of(),
                null,
                provenance
        );
        List<ProductIdentityEvidence> evidence = withUpid
                ? List.of(new ProductIdentityEvidence(
                        ProductIdentityEvidenceKind.UPID,
                        IdentityEvidenceStrength.TRUSTED_EXACT,
                        10_000,
                        List.of(identifier(
                                ExternalIdentifierType.UPID, "gid://shopify/p/universal-product")),
                        new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "GLOBAL_CATALOG", null)
                ))
                : List.of();
        return new CanonicalProduct(
                "grouped-product-v3_anchor",
                "Anchor",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                evidence,
                provenance,
                List.of(offer)
        );
    }

    private ResultProvenance provenance(ResultSourceType type, String source, String product) {
        return new ResultProvenance(
                PROVIDER,
                new DiscoverySourceIdentity(PROVIDER, type, source),
                null,
                identifier(ExternalIdentifierType.MERCHANT, "merchant"),
                identifier(ExternalIdentifierType.PRODUCT, product),
                identifier(ExternalIdentifierType.VARIANT, "gid://shopify/ProductVariant/reference-variant"),
                new ResultFreshness(OBSERVED_AT, null),
                new ResultSourceReference(type, source, null)
        );
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, PROVIDER.value(), value);
    }

    private EnsureUserProfileCommand profile(UUID userId) {
        return new EnsureUserProfileCommand(userId, "shopper@example.com", "Shopper", null);
    }

    private SearchSimilarUserProductsCommand command(UUID userId, String canonicalProductKey) {
        return new SearchSimilarUserProductsCommand(
                userId,
                canonicalProductKey,
                "trail running shoes",
                "192.0.2.10",
                "test-agent"
        );
    }

    private UserCanonicalProductSessionStore store() {
        return new UserCanonicalProductSessionStore(Duration.ofMinutes(30), 100);
    }

    private UserCanonicalProductReferencePersistenceService persistence(Optional<CanonicalProduct> product) {
        return new UserCanonicalProductReferencePersistenceService(null, null, null, List.of()) {
            @Override
            public Optional<CanonicalProduct> findProduct(UUID userId, String canonicalProductKey) {
                return product.filter(candidate -> USER_ID.equals(userId)
                        && candidate.key().equals(canonicalProductKey));
            }
        };
    }

    private static final class CapturingGroupedProductSearchService extends UserGroupedProductSearchService {
        private final UserGroupedProductSearchResult result = new UserGroupedProductSearchResult(
                "trail running shoes", "trail running shoes", "profile", false,
                0, 20, null, false, false, List.of(), 0, false, List.of());
        private SearchUserProductsCommand command;
        private CatalogDiscoveryFilters discoveryFilters;
        private CanonicalProduct anchor;
        private CatalogSimilarityReference reference;
        private int calls;

        private CapturingGroupedProductSearchService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        UserGroupedProductSearchResult searchSimilar(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand command,
                CatalogDiscoveryFilters discoveryFilters,
                CanonicalProduct anchor,
                CatalogSimilarityReference similarityReference
        ) {
            this.command = command;
            this.discoveryFilters = discoveryFilters;
            this.anchor = anchor;
            this.reference = similarityReference;
            calls++;
            return result;
        }
    }
}

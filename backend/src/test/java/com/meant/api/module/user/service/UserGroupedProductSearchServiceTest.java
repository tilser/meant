package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByMerchantsQuery;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserGroupedProductSearchResult;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.module.user.service.dto.UserProductSearchResult;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.ExactProductGroupingService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserGroupedProductSearchServiceTest {

    @Test
    void mapsTheFlatSearchThroughMerchantIntegrationIdentityWithoutChangingLegacyKeys() {
        UserProductSearchProductResult flatProduct = flatProduct();
        UserProductSearchResult flatResult = new UserProductSearchResult(
                "linen shirt",
                "linen shirt",
                "profile-hash",
                false,
                0,
                20,
                null,
                false,
                List.of(flatProduct)
        );
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "shopper@example.com",
                "Shopper",
                null
        );
        SearchUserProductsCommand searchCommand = new SearchUserProductsCommand(
                profileCommand.id(), "linen shirt", null, "127.0.0.1", "test", 0, 20);
        MerchantIntegrationResult integration = integration(flatProduct.merchantId());
        StubUserProductSearchService flatSearchService = new StubUserProductSearchService(flatResult);
        StubMerchantIntegrationLookupService integrationLookupService =
                new StubMerchantIntegrationLookupService(integration);
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                flatSearchService,
                integrationLookupService,
                new UserCanonicalProductCandidateMapper(),
                new ExactProductGroupingService()
        );

        UserGroupedProductSearchResult result = service.search(profileCommand, searchCommand);

        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.key()).startsWith("product_v1_").isNotEqualTo(flatProduct.productKey());
            assertThat(product.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.identity().provider().value()).isEqualTo("SHOPIFY");
                assertThat(offer.identity().merchantIntegrationId()).isEqualTo(integration.id());
                assertThat(offer.identity().externalMerchantIdentity().value())
                        .isEqualTo("gid://shopify/Shop/100");
                assertThat(offer.identity().externalProductIdentity().value())
                        .isEqualTo("gid://shopify/Product/200");
                assertThat(offer.identity().externalVariantIdentity().type())
                        .isEqualTo(ExternalIdentifierType.VARIANT);
                assertThat(offer.price().minorUnits()).isEqualTo(4200);
                assertThat(offer.price().currency()).isEqualTo("USD");
                assertThat(offer.provenance().getFirst().sourceReference().type())
                        .isEqualTo(ResultSourceType.MERCHANT_STOREFRONT);
            });
        });
        assertThat(flatProduct.productKey()).isEqualTo("legacy.example:legacy-product-key");
        assertThat(flatSearchService.profileCommand).isEqualTo(profileCommand);
        assertThat(flatSearchService.searchCommand).isEqualTo(searchCommand);
    }

    @Test
    void resolvesTheOnlyActiveIntegrationWhenItsEndpointIsAbsent() {
        UserProductSearchProductResult flatProduct = flatProduct();
        UserProductSearchResult flatResult = new UserProductSearchResult(
                "linen shirt",
                "linen shirt",
                "profile-hash",
                false,
                0,
                20,
                null,
                false,
                List.of(flatProduct)
        );
        EnsureUserProfileCommand profileCommand = new EnsureUserProfileCommand(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "shopper@example.com",
                "Shopper",
                null
        );
        MerchantIntegrationResult integration = integration(flatProduct.merchantId(), null);
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                new StubUserProductSearchService(flatResult),
                new StubMerchantIntegrationLookupService(integration),
                new UserCanonicalProductCandidateMapper(),
                new ExactProductGroupingService()
        );

        UserGroupedProductSearchResult result = service.search(
                profileCommand,
                new SearchUserProductsCommand(
                        profileCommand.id(), "linen shirt", null, "127.0.0.1", "test", 0, 20)
        );

        assertThat(result.products().getFirst().offers().getFirst().identity().merchantIntegrationId())
                .isEqualTo(integration.id());
    }

    @Test
    void omitsPriceWhenTheFlatCandidateHasNoCurrency() {
        UserProductSearchProductResult flatProduct = flatProduct(null, null);

        assertThat(new UserCanonicalProductCandidateMapper()
                .from(
                        flatProduct,
                        integration(flatProduct.merchantId()),
                        Instant.parse("2026-07-10T10:00:00Z"),
                        ResultSourceType.MERCHANT_STOREFRONT
                )
                .offer()
                .price()).isNull();
    }

    private UserProductSearchProductResult flatProduct() {
        return flatProduct("usd", "usd");
    }

    private UserProductSearchProductResult flatProduct(
            String priceCurrency,
            String selectedVariantPriceCurrency
    ) {
        UUID merchantId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        return new UserProductSearchProductResult(
                "legacy.example:legacy-product-key", "legacy-hash", merchantId, "shop.example", "Legacy merchant",
                "https://shop.example/mcp", 1, 0.9, 0.8, "gid://shopify/Product/200", "Linen shirt",
                "<p>A linen shirt</p>", "https://shop.example/products/linen-shirt",
                "https://shop.example/linen-shirt.jpg", 4200L, 4200L, priceCurrency, null, null, null, null,
                List.of(), List.of(), List.of("OEKO-TEX"), List.of("linen"), List.of(), List.of(), List.of(), true,
                null, "A linen shirt", "https://shop.example/linen-shirt.jpg", "42.00", "42.00", "usd",
                "gid://shopify/ProductVariant/300", "Natural / Medium", "42.00", selectedVariantPriceCurrency,
                "https://shop.example/linen-shirt.jpg", "Linen shirt", true, 1, 0.8, 1, 90, "Matches",
                List.of(), List.of(), null, null, null
        );
    }

    private MerchantIntegrationResult integration(UUID merchantId) {
        return integration(merchantId, "https://shop.example/mcp");
    }

    private MerchantIntegrationResult integration(UUID merchantId, String endpoint) {
        Instant now = Instant.parse("2026-07-10T10:00:00Z");
        return new MerchantIntegrationResult(
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                merchantId,
                MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                "gid://shopify/Shop/100",
                "shop.example",
                "shop.myshopify.com",
                endpoint,
                "2026-04-08",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                now,
                now,
                now
        );
    }

    private static final class StubUserProductSearchService extends UserProductSearchService {

        private final UserProductSearchResult result;
        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;

        private StubUserProductSearchService(UserProductSearchResult result) {
            super(null, null, null, null, null, null, null, null, null, null, null, null, null);
            this.result = result;
        }

        @Override
        public UserProductSearchResult search(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand searchCommand
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = searchCommand;
            return result;
        }
    }

    private static final class StubMerchantIntegrationLookupService extends MerchantIntegrationLookupService {

        private final MerchantIntegrationResult integration;

        private StubMerchantIntegrationLookupService(MerchantIntegrationResult integration) {
            super(null);
            this.integration = integration;
        }

        @Override
        public List<MerchantIntegrationResult> listByMerchants(ListMerchantIntegrationsByMerchantsQuery query) {
            assertThat(query.merchantIds()).containsExactly(integration.merchantId());
            return List.of(integration);
        }
    }
}

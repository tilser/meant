package com.meant.api.module.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.user.service.command.EnsureUserProfileCommand;
import com.meant.api.module.user.service.command.SearchUserProductsCommand;
import com.meant.api.module.user.service.dto.UserProductSearchCatalogInput;
import com.meant.api.module.user.service.dto.UserProductSearchPreparation;
import com.meant.api.module.user.service.dto.UserProductSearchProductResult;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryRequest;
import com.meant.api.plugin.catalog.common.dto.CatalogDiscoveryTerminalStatus;
import com.meant.api.plugin.catalog.common.dto.ExternalIdentifierType;
import com.meant.api.plugin.catalog.common.dto.FederatedCatalogDiscoveryResult;
import com.meant.api.plugin.catalog.common.dto.ProductCandidate;
import com.meant.api.plugin.catalog.common.dto.ResultSourceType;
import com.meant.api.plugin.catalog.common.service.ExactProductGroupingService;
import com.meant.api.plugin.catalog.common.service.FederatedCatalogDiscoveryMetrics;
import com.meant.api.plugin.catalog.common.service.FederatedCatalogDiscoveryProperties;
import com.meant.api.plugin.catalog.common.service.FederatedCatalogDiscoveryService;
import com.meant.api.plugin.catalog.shopify.ShopifyOfferIdentityStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserGroupedProductSearchServiceTest {

    @Test
    void groupsFederatedCandidatesWithoutUsingTheLegacyCache() {
        UserProductSearchProductResult flatProduct = flatProduct("usd", "usd");
        ProductCandidate candidate = candidateMapper().from(
                flatProduct,
                integration(flatProduct.merchantId()),
                Instant.parse("2026-07-10T10:00:00Z"),
                ResultSourceType.MERCHANT_STOREFRONT
        );
        StubPreparationService preparationService = new StubPreparationService(preparation());
        StubFederatedDiscoveryService discoveryService = new StubFederatedDiscoveryService(
                new FederatedCatalogDiscoveryResult(
                        CatalogDiscoveryTerminalStatus.SUCCESS,
                        List.of(),
                        List.of(candidate)
                )
        );
        UserGroupedProductSearchService service = new UserGroupedProductSearchService(
                preparationService,
                discoveryService,
                new ExactProductGroupingService()
        );
        EnsureUserProfileCommand profile = profile();
        SearchUserProductsCommand command = command(profile.id());

        var result = service.search(profile, command);

        assertThat(result.cached()).isFalse();
        assertThat(result.products()).singleElement().satisfies(product -> {
            assertThat(product.offers()).singleElement().satisfies(offer -> {
                assertThat(offer.identity().provider().value()).isEqualTo("SHOPIFY");
                assertThat(offer.identity().merchantScope().externalMerchantIdentity().value())
                        .isEqualTo("gid://shopify/Shop/100");
                assertThat(offer.identity().externalProductIdentity().value())
                        .isEqualTo("variant-product:v1:gid://shopify/ProductVariant/300");
                assertThat(offer.identity().externalVariantIdentity().type())
                        .isEqualTo(ExternalIdentifierType.VARIANT);
                assertThat(offer.price().minorUnits()).isEqualTo(4200);
            });
        });
        assertThat(discoveryService.request.query()).isEqualTo("linen shirt");
        assertThat(discoveryService.request.candidateLimit()).isEqualTo(21);
        assertThat(preparationService.profileCommand).isEqualTo(profile);
        assertThat(preparationService.searchCommand).isEqualTo(command);
    }

    @Test
    void omitsPriceWhenTheCandidateHasNoCurrency() {
        UserProductSearchProductResult flatProduct = flatProduct(null, null);

        ProductCandidate candidate = candidateMapper().from(
                flatProduct,
                integration(flatProduct.merchantId()),
                Instant.parse("2026-07-10T10:00:00Z"),
                ResultSourceType.MERCHANT_STOREFRONT
        );

        assertThat(candidate.offer().price()).isNull();
        assertThat(candidate.offer().identity().externalProductIdentity().value())
                .isEqualTo("variant-product:v1:gid://shopify/ProductVariant/300");
    }

    private UserProductSearchPreparation preparation() {
        return new UserProductSearchPreparation(
                "linen shirt",
                null,
                null,
                null,
                new UserProductSearchCatalogInput("linen shirt", "normalized", null, null, null),
                "normalized",
                "profile-hash",
                Instant.parse("2026-07-11T00:00:00Z"),
                0,
                20,
                21
        );
    }

    private EnsureUserProfileCommand profile() {
        return new EnsureUserProfileCommand(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "shopper@example.com",
                "Shopper",
                null
        );
    }

    private SearchUserProductsCommand command(UUID userId) {
        return new SearchUserProductsCommand(userId, "linen shirt", null, "127.0.0.1", "test", 0, 20);
    }

    private UserCanonicalProductCandidateMapper candidateMapper() {
        return new UserCanonicalProductCandidateMapper(List.of(new ShopifyOfferIdentityStrategy()));
    }

    private UserProductSearchProductResult flatProduct(String priceCurrency, String selectedVariantPriceCurrency) {
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
                "https://shop.example/mcp",
                "2026-04-08",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER,
                MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY,
                now,
                now,
                now
        );
    }

    private static final class StubPreparationService extends UserProductSearchPreparationService {

        private final UserProductSearchPreparation preparation;
        private EnsureUserProfileCommand profileCommand;
        private SearchUserProductsCommand searchCommand;

        private StubPreparationService(UserProductSearchPreparation preparation) {
            super(null, null, null, null, null, null);
            this.preparation = preparation;
        }

        @Override
        public UserProductSearchPreparation prepare(
                EnsureUserProfileCommand profileCommand,
                SearchUserProductsCommand command
        ) {
            this.profileCommand = profileCommand;
            this.searchCommand = command;
            return preparation;
        }
    }

    private static final class StubFederatedDiscoveryService extends FederatedCatalogDiscoveryService {

        private final FederatedCatalogDiscoveryResult result;
        private CatalogDiscoveryRequest request;

        private StubFederatedDiscoveryService(FederatedCatalogDiscoveryResult result) {
            super(
                    List.of(),
                    new FederatedCatalogDiscoveryProperties(Duration.ofSeconds(1)),
                    new FederatedCatalogDiscoveryMetrics(new SimpleMeterRegistry())
            );
            this.result = result;
        }

        @Override
        public FederatedCatalogDiscoveryResult search(CatalogDiscoveryRequest request) {
            this.request = request;
            return result;
        }
    }
}

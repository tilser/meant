package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.service.port.ExternalOfferCartRoutingProvider;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.LocalMerchantRouting;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationAuthStrategy;
import com.meant.api.module.merchant.constant.MerchantIntegrationKind;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationSource;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantIntegrationLookupService;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationResult;
import com.meant.api.module.merchant.service.query.ListMerchantIntegrationsByIdsQuery;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SelectedOfferCartRoutingServiceTest {

    @Test
    void checkoutRestorationUsesTheProviderCheckoutPolicyPathOnlyForCheckout() {
        MerchantCartProvider cartProvider = new MerchantCartProvider(
                null, "shop.test", "https://shop.test/cart", null);
        MerchantCartProvider checkoutProvider = new MerchantCartProvider(
                null, "shop.test", "https://shop.test/checkout", null);
        CartRoutingTarget persisted = new CartRoutingTarget(
                "SHOPIFY:merchant:shop-1:domain:shop.test",
                MerchantIntegrationProvider.SHOPIFY,
                null,
                "shop-1",
                cartProvider
        );
        AtomicBoolean cartRestoreCalled = new AtomicBoolean();
        AtomicBoolean checkoutRestoreCalled = new AtomicBoolean();
        ExternalOfferCartRoutingProvider external = new ExternalOfferCartRoutingProvider() {
            @Override
            public boolean supports(ResolvedSelectedOffer offer) {
                return false;
            }

            @Override
            public Optional<CartRoutingTarget> resolve(ResolvedSelectedOffer offer) {
                return Optional.empty();
            }

            @Override
            public boolean supportsPersisted(CartRoutingTarget target) {
                return true;
            }

            @Override
            public Optional<CartRoutingTarget> restore(CartRoutingTarget target) {
                cartRestoreCalled.set(true);
                return Optional.of(new CartRoutingTarget(
                        target.scopeKey(), target.provider(), null, target.externalMerchantId(), cartProvider));
            }

            @Override
            public Optional<CartRoutingTarget> restoreForCheckout(CartRoutingTarget target) {
                checkoutRestoreCalled.set(true);
                return Optional.of(new CartRoutingTarget(
                        target.scopeKey(), target.provider(), null, target.externalMerchantId(), checkoutProvider));
            }
        };
        SelectedOfferCartRoutingService service = new SelectedOfferCartRoutingService(
                new StubIntegrationLookup(), new StubProviderLookup(new MerchantCartProvider(
                        UUID.randomUUID(), "merchant.test", "https://merchant.test/api/ucp/mcp", null)),
                List.of(external), new CartBindingMetrics(new SimpleMeterRegistry()));

        CartRoutingTarget cartTarget = service.resolvePersistedExternal(persisted);
        CartRoutingTarget checkoutTarget = service.resolvePersistedExternalForCheckout(persisted);

        assertThat(cartTarget.merchantProvider().advertisedMcpEndpoint()).isEqualTo("https://shop.test/cart");
        assertThat(checkoutTarget.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo("https://shop.test/checkout");
        assertThat(cartRestoreCalled).isTrue();
        assertThat(checkoutRestoreCalled).isTrue();
    }

    @Test
    void genericUcpRoutesOnlyThroughTheExactActiveCartIntegration() {
        UUID integrationId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        MerchantIntegrationResult integration = new MerchantIntegrationResult(
                integrationId, merchantId, MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION, Set.of(MerchantIntegrationRole.CART),
                null, "merchant.test", null, "https://merchant.test/api/ucp/mcp", "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE, MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY, Instant.now(), Instant.now(), Instant.now());
        MerchantCartProvider provider = provider(integration, "merchant.test");
        SelectedOfferCartRoutingService service = new SelectedOfferCartRoutingService(
                new StubIntegrationLookup(integration), new StubProviderLookup(provider), List.of(),
                new CartBindingMetrics(new SimpleMeterRegistry()));

        var target = service.resolve(offer(integrationId));

        assertThat(target.provider()).isEqualTo(MerchantIntegrationProvider.GENERIC_UCP);
        assertThat(target.merchantIntegrationId()).isEqualTo(integrationId);
        assertThat(target.merchantProvider().merchantDomain()).isEqualTo("merchant.test");
        assertThat(target.merchantProvider().routingDomain()).isEqualTo("merchant.test");
        assertThat(target.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo("https://merchant.test/api/ucp/mcp");
        assertThat(target.scopeKey()).isEqualTo("GENERIC_UCP:integration:" + integrationId);
    }

    @Test
    void shopifyOfferKeepsExternalSellerIdentityWhenLocalRoutingIsResolved() {
        UUID integrationId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String shopId = "gid://shopify/Shop/1";
        MerchantIntegrationResult integration = new MerchantIntegrationResult(
                integrationId, merchantId, MerchantIntegrationProvider.SHOPIFY,
                MerchantIntegrationKind.MERCHANT_CONNECTION, Set.of(MerchantIntegrationRole.CART),
                shopId, "allbirds.com", shopId,
                "https://weareallbirds.myshopify.com/api/ucp/mcp", "1.0",
                MerchantIntegrationAuthStrategy.OAUTH_BEARER, MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY, Instant.now(), Instant.now(), Instant.now());
        MerchantCartProvider provider = provider(integration, "allbirds.com");
        SelectedOfferCartRoutingService service = new SelectedOfferCartRoutingService(
                new StubIntegrationLookup(integration), new StubProviderLookup(provider), List.of(),
                new CartBindingMetrics(new SimpleMeterRegistry()));

        var target = service.resolve(offer(integrationId, "SHOPIFY", shopId));

        assertThat(target.provider()).isEqualTo(MerchantIntegrationProvider.SHOPIFY);
        assertThat(target.merchantIntegrationId()).isEqualTo(integrationId);
        assertThat(target.externalMerchantId()).isEqualTo(shopId);
        assertThat(target.scopeKey()).isEqualTo("SHOPIFY:integration:" + integrationId);
        assertThat(target.merchantProvider().merchantDomain()).isEqualTo("allbirds.com");
        assertThat(target.merchantProvider().routingDomain())
                .isEqualTo("weareallbirds.myshopify.com");
        assertThat(target.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo("https://weareallbirds.myshopify.com/api/ucp/mcp");
    }

    @Test
    void catalogProvenanceRoutesThroughTheMerchantsSeparateActiveCartIntegration() {
        UUID catalogIntegrationId = UUID.randomUUID();
        UUID cartIntegrationId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        MerchantIntegrationResult catalogIntegration = new MerchantIntegrationResult(
                catalogIntegrationId, merchantId, MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                null, "merchant.test", null, "https://catalog.merchant.test/api/ucp/mcp", "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE, MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY, Instant.now(), Instant.now(), Instant.now());
        MerchantIntegrationResult cartIntegration = new MerchantIntegrationResult(
                cartIntegrationId, merchantId, MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION, Set.of(MerchantIntegrationRole.CART),
                null, "merchant.test", null, "https://cart.merchant.test/api/ucp/mcp", "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE, MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.DISCOVERY, Instant.now(), Instant.now(), Instant.now());
        MerchantCartProvider provider = provider(cartIntegration, "merchant.test");
        SelectedOfferCartRoutingService service = new SelectedOfferCartRoutingService(
                new StubIntegrationLookup(catalogIntegration, cartIntegration),
                new StubProviderLookup(provider), List.of(),
                new CartBindingMetrics(new SimpleMeterRegistry()));

        var target = service.resolve(offer(catalogIntegrationId));

        assertThat(target.merchantIntegrationId()).isEqualTo(cartIntegrationId);
        assertThat(target.scopeKey()).isEqualTo("GENERIC_UCP:integration:" + cartIntegrationId);
        assertThat(target.merchantProvider().merchantDomain()).isEqualTo("merchant.test");
        assertThat(target.merchantProvider().routingDomain()).isEqualTo("merchant.test");
    }

    @Test
    void capabilitySelectedCartRouteDoesNotRequireTheStaleBackfillRole() {
        UUID integrationId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        MerchantIntegrationResult staleBackfillIntegration = new MerchantIntegrationResult(
                integrationId, merchantId, MerchantIntegrationProvider.GENERIC_UCP,
                MerchantIntegrationKind.MERCHANT_CONNECTION,
                Set.of(MerchantIntegrationRole.STOREFRONT_CATALOG),
                null, "allbirds.com", null, "https://weareallbirds.myshopify.com/api/ucp/mcp", "2026-04-08",
                MerchantIntegrationAuthStrategy.NONE, MerchantIntegrationStatus.ACTIVE,
                MerchantIntegrationSource.LEGACY_MERCHANT_BACKFILL,
                Instant.now(), Instant.now(), Instant.now());
        MerchantCartProvider provider = provider(staleBackfillIntegration, "allbirds.com");
        SelectedOfferCartRoutingService service = new SelectedOfferCartRoutingService(
                new StubIntegrationLookup(staleBackfillIntegration),
                new StubProviderLookup(provider), List.of(),
                new CartBindingMetrics(new SimpleMeterRegistry()));

        var target = service.resolve(offer(integrationId));

        assertThat(target.merchantIntegrationId()).isEqualTo(integrationId);
        assertThat(target.scopeKey()).isEqualTo("GENERIC_UCP:integration:" + integrationId);
    }

    private MerchantCartProvider provider(MerchantIntegrationResult integration, String merchantDomain) {
        CommerceCapabilityDecision cart = new CommerceCapabilityDecision(
                CommerceOperation.CART, true, CapabilityAuthorizationDecision.notRequired(), true,
                CapabilityIntegrationHealth.HEALTHY, false, CapabilityAvailability.AVAILABLE,
                CommerceExecutionRail.PROVIDER_CART, List.of(), integration.id(), integration.provider());
        MerchantIntegrationRouting routing = new MerchantIntegrationRouting(
                integration.id(),
                integration.provider(),
                integration.roles(),
                integration.status(),
                integration.externalMerchantId(),
                integration.verifiedDomain(),
                integration.verifiedShopIdentity(),
                integration.endpoint()
        );
        return new MerchantCartProvider(
                integration.merchantId(),
                merchantDomain,
                merchantDomain,
                integration.endpoint(),
                null,
                List.of(routing),
                new MerchantExecutionPolicy(List.of(cart)),
                integration.capturedAt(),
                Set.of()
        );
    }

    private ResolvedSelectedOffer offer(UUID integrationId) {
        return offer(integrationId, "GENERIC_UCP", null);
    }

    private ResolvedSelectedOffer offer(UUID integrationId, String providerName, String externalMerchantId) {
        ProviderIdentity provider = new ProviderIdentity(providerName);
        ExternalIdentifier product = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), "product-1");
        ExternalIdentifier variant = new ExternalIdentifier(
                ExternalIdentifierType.VARIANT, provider.value(), "variant-1");
        LocalMerchantRouting routing = new LocalMerchantRouting(integrationId);
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.MERCHANT_STOREFRONT, integrationId.toString());
        ExternalIdentifier externalMerchant = externalMerchantId == null ? null : new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), externalMerchantId);
        OfferIdentity identity = new OfferIdentity(
                provider,
                externalMerchantId == null
                        ? OfferMerchantScope.localIntegrationFallback(integrationId)
                        : OfferMerchantScope.external(externalMerchant),
                product, variant,
                List.of(), List.of(), null);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, routing, externalMerchant, product, variant, new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.MERCHANT_STOREFRONT, "merchant", null));
        CatalogProductReference reference = new CatalogProductReference(
                identity.key(), source, null, routing, externalMerchant, product, variant, List.of());
        return new ResolvedSelectedOffer("canonical", identity.key(), identity, provenance, reference);
    }

    private static final class StubIntegrationLookup extends MerchantIntegrationLookupService {
        private final List<MerchantIntegrationResult> integrations;

        private StubIntegrationLookup(MerchantIntegrationResult... integrations) {
            super(null);
            this.integrations = List.of(integrations);
        }

        @Override
        public List<MerchantIntegrationResult> listByIds(ListMerchantIntegrationsByIdsQuery query) {
            return integrations.stream()
                    .filter(integration -> query.integrationIds().contains(integration.id()))
                    .toList();
        }
    }

    private static final class StubProviderLookup extends MerchantCartProviderLookupService {
        private final MerchantCartProvider provider;

        private StubProviderLookup(MerchantCartProvider provider) {
            super(null, null, null, null);
            this.provider = provider;
        }

        @Override
        public Optional<MerchantCartProvider> findById(UUID merchantId) {
            return provider.merchantId().equals(merchantId) ? Optional.of(provider) : Optional.empty();
        }
    }
}

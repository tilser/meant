package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.dto.UserCommerceContextResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartMerchantScopeIsolationTest {

    @Test
    void differentSellersCannotShareOneRemoteCart() {
        CartBindingMetrics metrics = new CartBindingMetrics(new SimpleMeterRegistry());
        UserSelectedOfferResolutionService resolution = mock(UserSelectedOfferResolutionService.class);
        when(resolution.resolveAll(any())).thenAnswer(invocation ->
                invocation.<ResolveUserSelectedOffersQuery>getArgument(0)
                        .offerKeys().stream().map(CartMerchantScopeIsolationTest::offer).toList());
        SelectedOfferCartRoutingService routing = mock(SelectedOfferCartRoutingService.class);
        when(routing.resolve(any())).thenAnswer(invocation -> route(invocation.getArgument(0)));
        UserCommerceContextService commerceContext = mock(UserCommerceContextService.class);
        when(commerceContext.find(any())).thenReturn(new UserCommerceContextResult(null));
        CartService service = new CartService(
                mock(com.meant.api.module.merchant.service.MerchantCartProviderLookupService.class),
                mock(CartBuyerContextService.class), mock(CartPersistenceService.class),
                mock(MerchantCartPluginDispatchService.class),
                mock(com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService.class),
                mock(com.meant.api.module.checkout.service.NativeCheckoutCompletionService.class),
                mock(com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService.class),
                mock(CartResultMapper.class), mock(CheckoutResultMapper.class),
                mock(CartCheckoutConsentService.class), resolution, routing,
                mock(CartOfferRevalidationService.class), metrics, commerceContext,
                new CartReplacementService(new CartLineOfferIdentityMapper(new tools.jackson.databind.ObjectMapper()),
                        new CartFulfillmentReplacementService()),
                new CommerceMutationPolicy(
                        new com.meant.api.module.cart.properties.CartRetryProperties(java.time.Duration.ofSeconds(2)),
                        new CartRetrySleeper()),
                new CheckoutUpdateReconciliationService(), new CheckoutCancellationPolicy(),
                mock(com.meant.api.module.user.service.UserCheckoutDetailsService.class));
        CreateCartCommand command = new CreateCartCommand(
                UUID.randomUUID(), null, null,
                List.of(new CreateCartCommand.AddItem("shop-1", 1), new CreateCartCommand.AddItem("shop-2", 1)),
                null, List.of(), List.of(), List.of(), List.of(), List.of(), null);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(CartException.class)
                .extracting("bindingFailure")
                .isEqualTo(CartException.BindingFailure.CROSS_SCOPE_REPLAY);
    }

    private static ResolvedSelectedOffer offer(String offerKey) {
            ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
            ExternalIdentifier merchant = id(ExternalIdentifierType.MERCHANT, offerKey);
            ExternalIdentifier product = id(ExternalIdentifierType.PRODUCT, "product-" + offerKey);
            ExternalIdentifier variant = id(ExternalIdentifierType.VARIANT, "variant-" + offerKey);
            DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                    provider, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG");
            OfferIdentity identity = new OfferIdentity(
                    provider, OfferMerchantScope.external(merchant), product, variant, List.of(), List.of(), null);
            ResultProvenance provenance = new ResultProvenance(
                    provider, source, null, merchant, product, variant, new ResultFreshness(Instant.now(), null),
                    new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "catalog", null));
            CatalogProductReference reference = new CatalogProductReference(
                    identity.key(), source, null, null, merchant, product, variant, List.of());
            return new ResolvedSelectedOffer("canonical", offerKey, identity, provenance, reference);
    }

    private static CartRoutingTarget route(ResolvedSelectedOffer offer) {
            String merchant = offer.identity().merchantScope().externalMerchantIdentity().value();
            return new CartRoutingTarget(
                    "SHOPIFY:merchant:" + merchant,
                    MerchantIntegrationProvider.SHOPIFY,
                    null,
                    merchant,
                    new MerchantCartProvider(null, null, "https://cart.shopify.test/api/ucp/mcp", null,
                            List.of(), MerchantExecutionPolicy.unavailable()));
    }

    private static ExternalIdentifier id(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }
}

package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.constant.CartAppliedCodeType;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartAppliedCode;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.merchant.entity.Merchant;
import com.meant.api.module.merchant.repository.MerchantCapabilityRepository;
import com.meant.api.module.merchant.repository.MerchantIntegrationRepository;
import com.meant.api.module.merchant.repository.MerchantRepository;
import com.meant.api.module.merchant.properties.MerchantExecutionPolicyProperties;
import com.meant.api.module.merchant.service.CapabilityExecutionPolicyEvaluator;
import com.meant.api.module.user.repository.UserSettingsLocationRepository;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantExecutionPolicyService;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.cart.service.command.CancelCartCommand;
import com.meant.api.module.cart.service.command.CompleteCheckoutCommand;
import com.meant.api.module.cart.service.command.CreateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCartCommand;
import com.meant.api.module.cart.service.command.UpdateCheckoutCommand;
import com.meant.api.module.cart.service.dto.CartResult;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.dto.CartDeliveryOptionSelectionInput;
import com.meant.api.module.cart.service.dto.CheckoutResult;
import com.meant.api.module.cart.service.query.GetCartQuery;
import com.meant.api.module.cart.service.query.GetCheckoutQuery;
import com.meant.api.module.checkout.service.CheckoutPurchaseAttributionService;
import com.meant.api.module.user.service.UserCommerceContextService;
import com.meant.api.module.user.service.UserSelectedOfferResolutionService;
import com.meant.api.module.user.exception.SelectedOfferResolutionException;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.module.catalog.service.dto.*;
import com.meant.api.plugin.cart.cancel.dto.CancelCartRequest;
import com.meant.api.plugin.cart.cancel.dto.CancelCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.cart.common.dto.CartDeliveryOptionSelection;
import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.plugin.cart.create.dto.CreateCartRequest;
import com.meant.api.plugin.cart.get.dto.GetCartRequest;
import com.meant.api.plugin.cart.update.dto.UpdateCartRequest;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import com.meant.api.plugin.checkout.extension.fulfillment.dto.CheckoutFulfillment.ShippingDestination;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.checkout.service.NativeCheckoutCompletionService;
import com.meant.api.module.checkout.service.command.RecordCheckoutOpenedCommand;
import com.meant.api.module.checkout.service.dto.NativeCheckoutResult;
import com.meant.api.module.checkout.service.dto.NativeCheckoutStatus;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.plugin.checkout.create.dto.CreateCheckoutRequest;
import com.meant.api.plugin.checkout.get.dto.GetCheckoutRequest;
import com.meant.api.plugin.checkout.update.dto.UpdateCheckoutRequest;
import com.meant.api.plugin.support.UcpSession;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class CartServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CART_REMOTE_CREATED_AT = Instant.parse("2026-06-16T11:05:00Z");
    private static final Instant CART_REMOTE_UPDATED_AT = Instant.parse("2026-06-16T11:05:01Z");

    private FakeMerchantRepository merchantRepository;
    private FakeCartRepository cartRepository;
    private FakeCartDispatchService cartDispatchService;
    private FakeCheckoutDispatchService checkoutDispatchService;
    private NativeCheckoutCompletionService nativeCheckoutCompletionService;
    private CheckoutPurchaseAttributionService checkoutPurchaseAttributionService;
    private CartPersistenceService cartPersistenceService;
    private CartService cartService;
    private UserSelectedOfferResolutionService offerResolution;
    private SelectedOfferCartRoutingService routing;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantRepository = new FakeMerchantRepository();
        cartRepository = new FakeCartRepository();
        cartDispatchService = new FakeCartDispatchService();
        checkoutDispatchService = new FakeCheckoutDispatchService();
        nativeCheckoutCompletionService = mock(NativeCheckoutCompletionService.class);
        checkoutPurchaseAttributionService = mock(CheckoutPurchaseAttributionService.class);
        cartPersistenceService = new CartPersistenceService(
                cartRepository.proxy(),
                new ObjectMapper()
        );
        merchant = merchant();
        merchantRepository.save(merchant);
        MerchantCartProviderLookupService providerLookup = new MerchantCartProviderLookupService(
                merchantRepository.proxy(), merchantCapabilityRepositoryProxy(), merchantIntegrationRepositoryProxy(),
                new MerchantExecutionPolicyService(
                        new MerchantExecutionPolicyProperties(true, true, true, false, false, true, true),
                        new CapabilityExecutionPolicyEvaluator(), List.of()));
        UserCommerceContextService commerceContextService =
                new UserCommerceContextService(userSettingsLocationRepositoryProxy());
        offerResolution = mock(UserSelectedOfferResolutionService.class);
        when(offerResolution.resolveAll(any())).thenAnswer(invocation ->
                invocation.<com.meant.api.module.user.service.query.ResolveUserSelectedOffersQuery>getArgument(0)
                        .offerKeys().stream().map(this::resolvedOffer).toList());
        routing = mock(SelectedOfferCartRoutingService.class);
        when(routing.resolve(any())).thenAnswer(invocation -> new com.meant.api.module.cart.service.dto.CartRoutingTarget(
                "LEGACY:merchant:" + merchant.getId(),
                com.meant.api.module.merchant.constant.MerchantIntegrationProvider.GENERIC_UCP,
                null, null, providerLookup.findById(merchant.getId()).orElseThrow()));
        CartOfferRevalidationService revalidation = mock(CartOfferRevalidationService.class);
        CartBindingMetrics metrics = new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        cartService = new CartService(
                providerLookup,
                new CartBuyerContextService(commerceContextService),
                cartPersistenceService,
                cartDispatchService,
                checkoutDispatchService,
                nativeCheckoutCompletionService,
                checkoutPurchaseAttributionService,
                new CartResultMapper(new ObjectMapper()),
                new CheckoutResultMapper(new ObjectMapper(), new CheckoutExecutionPlanner()),
                null,
                offerResolution,
                routing,
                revalidation,
                metrics,
                commerceContextService,
                new CartReplacementService(new CartLineOfferIdentityMapper(new ObjectMapper()),
                        new CartFulfillmentReplacementService()),
                new CommerceMutationPolicy(
                        new com.meant.api.module.cart.properties.CartRetryProperties(java.time.Duration.ofSeconds(2)),
                        new CartRetrySleeper()),
                new CheckoutUpdateReconciliationService(),
                new CheckoutCancellationPolicy()
        );
        cartDispatchService.cartToolResult = cartToolResult();
    }

    private ResolvedSelectedOffer resolvedOffer(String key) {
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        ExternalIdentifier product = new ExternalIdentifier(ExternalIdentifierType.PRODUCT, provider.value(), key);
        ExternalIdentifier variant = new ExternalIdentifier(ExternalIdentifierType.VARIANT, provider.value(), key);
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.MERCHANT_STOREFRONT, merchant.getDomain());
        OfferIdentity identity = new OfferIdentity(
                provider, OfferMerchantScope.localIntegrationFallback(merchant.getId()), product, variant,
                List.of(), List.of(), null);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, null, product, variant, new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.MERCHANT_STOREFRONT, merchant.getDomain(), null));
        CatalogProductReference reference = new CatalogProductReference(
                key, source, null, null, null, product, variant, List.of());
        return new ResolvedSelectedOffer("canonical", key, identity, provenance, reference);
    }

    private ResolvedSelectedOffer resolvedOfferWithoutVariant(String key) {
        ProviderIdentity provider = new ProviderIdentity("GENERIC_UCP");
        ExternalIdentifier product = new ExternalIdentifier(ExternalIdentifierType.PRODUCT, provider.value(), key);
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.MERCHANT_STOREFRONT, merchant.getDomain());
        OfferIdentity identity = new OfferIdentity(
                provider, OfferMerchantScope.localIntegrationFallback(merchant.getId()), product, null,
                List.of(), List.of(), null);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, null, product, null, new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.MERCHANT_STOREFRONT, merchant.getDomain(), null));
        CatalogProductReference reference = new CatalogProductReference(
                key, source, null, null, null, product, null, List.of());
        return new ResolvedSelectedOffer("canonical", key, identity, provenance, reference);
    }

    private ResolvedSelectedOffer resolvedShopifyOfferWithCanonicalProductAnchor(String key) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        ExternalIdentifier merchantIdentity = new ExternalIdentifier(
                ExternalIdentifierType.MERCHANT, provider.value(), "gid://shopify/Shop/1");
        ExternalIdentifier canonicalProduct = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), "variant-product:v1:" + key);
        ExternalIdentifier wireProduct = new ExternalIdentifier(
                ExternalIdentifierType.PRODUCT, provider.value(), "gid://shopify/p/upid-42");
        ExternalIdentifier variant = new ExternalIdentifier(ExternalIdentifierType.VARIANT, provider.value(), key);
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG");
        OfferIdentity identity = new OfferIdentity(
                provider, OfferMerchantScope.external(merchantIdentity), canonicalProduct, variant,
                List.of(), List.of(), null);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, merchantIdentity, "merchant.example", wireProduct, variant,
                new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG", null));
        CatalogProductReference reference = new CatalogProductReference(
                key, source, null, null, merchantIdentity, "merchant.example", wireProduct, variant, List.of());
        return new ResolvedSelectedOffer("canonical", key, identity, provenance, reference);
    }

    @Test
    void createCallsRemoteCartAndSavesSnapshot() {
        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.remoteCartId()).isEqualTo("gid://shopify/Cart/1");
        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().remoteCartLineId()).isEqualTo("gid://shopify/CartLine/1");
        assertThat(cartRepository.saveCount).isEqualTo(1);
        assertThat(cartDispatchService.createCount).isEqualTo(1);
        assertThat(cartDispatchService.updateCount).isZero();
    }

    @Test
    void createUsesRehydratedShopifyWireIdentityAndPropagatesBuyerIp() {
        String variantId = "gid://shopify/ProductVariant/42";
        doReturn(List.of(resolvedShopifyOfferWithCanonicalProductAnchor(variantId)))
                .when(offerResolution).resolveAll(any());

        cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem(variantId, 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "203.0.113.42"
        ));

        assertThat(cartDispatchService.lastCreateRequest.addItems()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isNull();
            assertThat(item.productVariantId()).isEqualTo(variantId);
        });
        assertThat(cartDispatchService.lastCallContext.buyerIp()).isEqualTo("203.0.113.42");
    }

    @Test
    void variantlessGenericSelectionFailsTypedBeforeRoutingOrCartMutation() {
        doReturn(List.of(resolvedOfferWithoutVariant("product-only")))
                .when(offerResolution).resolveAll(any());

        assertThatThrownBy(() -> cartService.create(new CreateCartCommand(
                USER_ID, merchant.getId(), null,
                List.of(new CreateCartCommand.AddItem("product-only", 1)), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null)))
                .isInstanceOf(SelectedOfferResolutionException.class)
                .extracting("failure")
                .isEqualTo(SelectedOfferResolutionException.Failure.UNSUPPORTED_SELECTION);

        assertThat(cartDispatchService.createCount).isZero();
        verifyNoInteractions(routing);
    }

    @Test
    void createSavesAppliedCodesAndAdjustedTotal() {
        cartDispatchService.cartToolResult = cartToolResultWithAppliedCodes();

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("SAVE5"),
                List.of("CARD1234"),
                null
        ));

        assertThat(result.subtotalAmount()).isEqualTo("14.95");
        assertThat(result.totalAmount()).isEqualTo("7.95");
        assertThat(result.appliedCodes()).hasSize(2);
        assertThat(result.appliedCodes()).extracting("type")
                .containsExactly(CartAppliedCodeType.DISCOUNT, CartAppliedCodeType.GIFT_CARD);
        assertThat(result.appliedCodes()).extracting("code").containsExactly("SAVE5", "CARD1234");
        assertThat(result.appliedCodes()).extracting("amount").containsExactly("5.00", "2.00");
    }

    @Test
    void createCarriesDeliveryGroupsFromRemoteSnapshot() {
        cartDispatchService.cartToolResult = cartToolResult(List.of(cartLine()), 1, List.of(deliveryGroup()));

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.deliveryGroups()).hasSize(1);
        assertThat(result.deliveryGroups().getFirst().id()).isEqualTo("delivery-group-1");
        assertThat(result.deliveryGroups().getFirst().deliveryOptions()).extracting("handle")
                .containsExactly("standard", "express");
        assertThat(result.deliveryGroups().getFirst().selectedDeliveryOption().handle()).isEqualTo("standard");
    }

    @Test
    void getRefreshPreservesKnownFullGiftCardCodeWhenMerchantReturnsLastCharacters() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stale-checkout");
        cart.replaceAppliedCodes(List.of(CartAppliedCode.builder()
                .type(CartAppliedCodeType.GIFT_CARD)
                .code("CARD1234")
                .label("Gift card")
                .displayOrder(0)
                .build()));
        cartRepository.save(cart);
        cartDispatchService.cartToolResult = cartToolResultWithAppliedCodes();

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, true));

        assertThat(result.appliedCodes())
                .filteredOn(code -> code.type() == CartAppliedCodeType.GIFT_CARD)
                .extracting("code")
                .containsExactly("CARD1234");
    }

    @Test
    void getWithoutRefreshReturnsDeliveryGroupsFromStoredSnapshot() {
        cartDispatchService.cartToolResult = cartToolResult(List.of(cartLine()), 1, List.of(deliveryGroup()));
        CartResult created = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));
        cartDispatchService.getCount = 0;

        CartResult result = cartService.get(new GetCartQuery(created.cartId(), USER_ID, false));

        assertThat(result.deliveryGroups()).hasSize(1);
        assertThat(result.deliveryGroups().getFirst().deliveryOptions()).extracting("cost.amount")
                .containsExactly("5.00", "12.00");
        assertThat(cartDispatchService.getCount).isZero();
    }

    @Test
    void cartResultMapperReturnsEmptyDeliveryGroupsWhenStoredSnapshotCannotBeParsed() {
        Cart cart = cart(UUID.randomUUID(), "https://merchant.example/checkout", UUID.randomUUID(), "not-json");

        CartResult result = new CartResultMapper(new ObjectMapper()).from(cart);

        assertThat(result.deliveryGroups()).isEmpty();
    }

    @Test
    void cartResultMapperFiltersNullDeliveryGroupsAndOptions() {
        UcpCartResponse.DeliveryGroup group = new UcpCartResponse.DeliveryGroup(
                "delivery-group-1",
                "delivery-group-handle-1",
                Arrays.asList(null, deliveryOption("standard", true)),
                null
        );
        UcpCartResponse response = cartToolResponse(List.of(cartLine()), 1, Arrays.asList(null, group));
        Cart cart = cart(UUID.randomUUID(), "https://merchant.example/checkout", UUID.randomUUID(), raw(response));

        CartResult result = new CartResultMapper(new ObjectMapper()).from(cart);

        assertThat(result.deliveryGroups()).hasSize(1);
        assertThat(result.deliveryGroups().getFirst().deliveryOptions()).extracting("handle")
                .containsExactly("standard");
    }

    @Test
    void getWithoutRefreshUsesStoredSnapshotOnly() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, false));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartDispatchService.getCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void getWithRefreshCallsRemoteAndSavesSnapshot() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/stale-checkout"));

        CartResult result = cartService.get(new GetCartQuery(cartId, USER_ID, true));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartDispatchService.getCount).isEqualTo(1);
        assertThat(cartDispatchService.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(cartRepository.saveCount).isEqualTo(1);
        assertThat(cartRepository.findWithLinesCount).isEqualTo(2);
    }

    @Test
    void getRefreshPreservesActiveCheckoutSessionMetadataAndHandoff() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stored-checkout");
        Instant synchronizedAt = Instant.parse("2026-06-16T11:07:00Z");
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stored", "processing-wire",
                "https://merchant.example/stored-checkout", "https://merchant.example/stored-continue",
                "{\"legacy\":\"permitted\"}", "2026-04-08", "PROCESSING", synchronizedAt);
        cartRepository.save(cart);

        cartService.get(new GetCartQuery(cartId, USER_ID, true));

        Cart saved = cartRepository.carts.get(cartId);
        assertThat(saved.getCheckoutId()).isEqualTo("gid://shopify/Checkout/stored");
        assertThat(saved.getCheckoutStatus()).isEqualTo("processing-wire");
        assertThat(saved.getCheckoutLifecycleState()).isEqualTo("PROCESSING");
        assertThat(saved.getCheckoutProtocolVersion()).isEqualTo("2026-04-08");
        assertThat(saved.getCheckoutSynchronizedAt()).isEqualTo(synchronizedAt);
        assertThat(saved.getCheckoutUrl()).isEqualTo("https://merchant.example/stored-checkout");
        assertThat(saved.getContinueUrl()).isEqualTo("https://merchant.example/stored-continue");
        assertThat(saved.getRawCheckoutResponse()).isEqualTo("{\"legacy\":\"permitted\"}");
    }

    @Test
    void updateMapsLocalCartLineIdsToRemoteCartLineIds() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout", cartLineId));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(new UpdateCartCommand.UpdateItem(cartLineId, null, 2)),
                List.of(cartLineId),
                List.of("gid://shopify/CartLine/1"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(cartDispatchService.lastUpdateRequest.updateItems()).extracting("id")
                .containsExactly("gid://shopify/CartLine/1");
        assertThat(cartDispatchService.lastUpdateRequest.removeLineIds()).containsExactly("gid://shopify/CartLine/1");
        assertThat(cartDispatchService.lastUpdateRequest.removeItems()).hasSize(1);
        assertThat(cartDispatchService.lastUpdateRequest.removeItems().getFirst().productVariantId())
                .isEqualTo("gid://shopify/ProductVariant/1");
        assertThat(cartDispatchService.lastUpdateRequest.removeItems().getFirst().quantity()).isZero();
        assertThat(cartRepository.findWithLinesCount).isEqualTo(2);
    }

    @Test
    void updateCartInvalidatesActiveCheckoutSession() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stored-checkout");
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stale", "incomplete",
                "https://merchant.example/stored-checkout", "https://merchant.example/stored-continue",
                "{}", "2026-04-08", "INCOMPLETE", Instant.now());
        cartRepository.save(cart);

        cartService.update(new UpdateCartCommand(
                cartId, USER_ID, List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null));

        Cart saved = cartRepository.carts.get(cartId);
        assertThat(saved.getCheckoutId()).isNull();
        assertThat(saved.getCheckoutStatus()).isNull();
        assertThat(saved.getCheckoutLifecycleState()).isNull();
        assertThat(saved.getCheckoutProtocolVersion()).isNull();
        assertThat(saved.getCheckoutSynchronizedAt()).isNull();
        assertThat(saved.getRawCheckoutResponse()).isNull();
    }

    @Test
    void updateUsesRemoteRemoveIdWhenLocalRemoveIdIsStale() {
        UUID cartId = UUID.randomUUID();
        UUID staleCartLineId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout", UUID.randomUUID()));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(),
                List.of(staleCartLineId),
                List.of("gid://shopify/CartLine/1"),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(cartDispatchService.lastUpdateRequest.removeLineIds()).containsExactly("gid://shopify/CartLine/1");
        assertThat(cartDispatchService.lastUpdateRequest.removeItems()).hasSize(1);
        assertThat(cartDispatchService.lastUpdateRequest.removeItems().getFirst().quantity()).isZero();
    }

    @Test
    void updateRejectsCrossedLocalAndRemoteLineIdsBeforeRemoteIo() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, null);
        UUID secondId = UUID.randomUUID();
        CartLine second = CartLine.builder()
                .id(secondId).remoteCartLineId("gid://shopify/CartLine/2")
                .productId("gid://shopify/Product/2").productVariantId("gid://shopify/ProductVariant/2")
                .quantity(1).rawLineResponse("{}").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        cart.replaceLines(new ArrayList<>(java.util.stream.Stream.concat(
                cart.getLines().stream(), java.util.stream.Stream.of(second)).toList()));
        cartRepository.save(cart);

        assertThatThrownBy(() -> cartService.update(new UpdateCartCommand(
                cartId, USER_ID, List.of(),
                List.of(new UpdateCartCommand.UpdateItem(
                        cart.getLines().getFirst().getId(), "gid://shopify/CartLine/2", 3)),
                List.of(), List.of(), null, null, null, null, null, null, null)))
                .isInstanceOf(CartException.class)
                .hasMessageContaining("different lines");
        assertThat(cartDispatchService.updateCount).isZero();
        assertThat(cartDispatchService.getCount).isZero();
    }

    @Test
    void updateOmitsCodesWhenNoCodeChangeIsRequested() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout", cartLineId));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(new UpdateCartCommand.UpdateItem(cartLineId, null, 2)),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null
        ));

        assertThat(cartDispatchService.lastUpdateRequest.discountCodes()).isNull();
        assertThat(cartDispatchService.lastUpdateRequest.giftCardCodes()).isNull();
    }

    @Test
    void updateForwardsSelectedDeliveryOptions() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(new CartDeliveryOptionSelectionInput(null, "delivery-group-1", "express")),
                List.of(),
                List.of(),
                null
        ));

        assertThat(cartDispatchService.lastUpdateRequest.selectedDeliveryOptions())
                .containsExactly(new CartDeliveryOptionSelection(null, "delivery-group-1", "express"));
    }

    @Test
    void updateRejectedByMerchantLeavesStoredCartUnchanged() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));
        cartDispatchService.updateException = CartException.rejected("Discount code EXPIRED was not accepted by the merchant.");

        assertThatThrownBy(() -> cartService.update(new UpdateCartCommand(
                cartId,
                USER_ID,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("EXPIRED"),
                null,
                null
        )))
                .isInstanceOf(CartException.class)
                .hasMessage("Discount code EXPIRED was not accepted by the merchant.");

        assertThat(cartRepository.saveCount).isZero();
        assertThat(cartRepository.carts.get(cartId).getTotalAmount()).isNull();
        assertThat(cartRepository.carts.get(cartId).getCheckoutUrl()).isEqualTo("https://merchant.example/checkout");
    }

    @Test
    void createIgnoresNullRemoteCartLinesWhenSavingSnapshot() {
        cartDispatchService.cartToolResult = cartToolResult(Arrays.asList(null, cartLine()), null);

        CartResult result = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        assertThat(result.totalQuantity()).isEqualTo(1);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().getFirst().remoteCartLineId()).isEqualTo("gid://shopify/CartLine/1");
    }

    @Test
    void checkoutRefreshesWhenStoredCheckoutUrlIsMissing() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, null));

        CheckoutResult result = cartService.checkout(
                new GetCheckoutQuery(cartId, USER_ID, false, "203.0.113.42"));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(result.continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(checkoutDispatchService.createCount).isEqualTo(1);
        assertThat(checkoutDispatchService.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(checkoutDispatchService.lastCallContext.buyerIp()).isEqualTo("203.0.113.42");
        assertThat(cartDispatchService.getCount).isZero();
        assertThat(cartRepository.findWithLinesCount).isEqualTo(2);
        assertNoInventoryAttribution();
    }

    @Test
    void concurrentCheckoutCreationConvergesOnOneLogicalCheckout() throws Exception {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, null));
        checkoutDispatchService.concurrentCreates = new CountDownLatch(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<CheckoutResult> first = executor.submit(
                    () -> cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, false)));
            Future<CheckoutResult> second = executor.submit(
                    () -> cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, false)));

            CheckoutResult firstResult = first.get(5, TimeUnit.SECONDS);
            CheckoutResult secondResult = second.get(5, TimeUnit.SECONDS);

            assertThat(firstResult.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
            assertThat(secondResult.checkoutId()).isEqualTo(firstResult.checkoutId());
            assertThat(cartRepository.carts.get(cartId).getCheckoutId()).isEqualTo(firstResult.checkoutId());
            assertThat(checkoutDispatchService.createCount).isEqualTo(2);
        }
    }

    @Test
    void checkoutRefreshesEmptyLocalCartBeforeCreatingCheckout() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, null);
        cart.replaceLines(List.of());
        cartRepository.save(cart);

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, true));

        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(cartDispatchService.getCount).isEqualTo(1);
        assertThat(cartDispatchService.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(checkoutDispatchService.createCount).isEqualTo(1);
        assertThat(checkoutDispatchService.lastRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(cartRepository.carts.get(cartId).getLines()).hasSize(1);
    }

    @Test
    void checkoutReturnsStoredUrlWithoutRefresh() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(
                cartId,
                "https://merchant.example/stored-checkout",
                "https://merchant.example/stored-continue"
        );
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stored",
                "incomplete",
                "https://merchant.example/stored-checkout",
                "https://merchant.example/stored-continue",
                "{}",
                Instant.parse("2026-06-16T11:07:00Z")
        );
        cartRepository.save(cart);

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, false));

        assertThat(result.checkoutId()).isEqualTo("gid://shopify/Checkout/stored");
        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/stored-checkout");
        assertThat(result.continueUrl()).isEqualTo("https://merchant.example/stored-continue");
        assertThat(cartDispatchService.getCount).isZero();
        assertThat(checkoutDispatchService.createCount).isZero();
        assertNoInventoryAttribution();
    }

    @Test
    void checkoutRefreshesStoredCheckoutSessionWhenRefreshIsRequested() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stored-checkout");
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stored",
                "incomplete",
                "https://merchant.example/stored-checkout",
                null,
                "{}",
                Instant.parse("2026-06-16T11:07:00Z")
        );
        cartRepository.save(cart);

        CheckoutResult result = cartService.checkout(new GetCheckoutQuery(cartId, USER_ID, true));

        assertThat(result.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(result.checkoutUrl()).isEqualTo("https://merchant.example/checkout");
        assertThat(result.continueUrl()).isEqualTo("https://merchant.example/continue");
        assertThat(checkoutDispatchService.createCount).isZero();
        assertThat(checkoutDispatchService.getCount).isEqualTo(1);
        assertThat(checkoutDispatchService.lastCheckoutId).isEqualTo("gid://shopify/Checkout/stored");
        assertThat(cartDispatchService.getCount).isZero();
        assertNoInventoryAttribution();
    }

    @Test
    void verifiedNativeCompletionUsesSharedAttemptAttributionFallback() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stored-checkout");
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stored",
                "incomplete",
                "https://merchant.example/stored-checkout",
                null,
                "{}",
                Instant.parse("2026-06-16T11:07:00Z")
        );
        UUID attemptId = cart.getCheckoutAttemptId();
        cartRepository.save(cart);
        when(nativeCheckoutCompletionService.complete(any(), any(), any())).thenReturn(new NativeCheckoutResult(
                NativeCheckoutStatus.COMPLETED,
                "gid://shopify/Checkout/stored",
                "order-1",
                null,
                List.of(),
                true
        ));

        cartService.completeCheckout(new CompleteCheckoutCommand(
                cartId,
                USER_ID,
                UUID.randomUUID(),
                "gid://shopify/Checkout/stored",
                List.of(mock(com.meant.api.plugin.payment.common.dto.PaymentInstrument.class)),
                "native-completion-key",
                false,
                null,
                null
        ));

        ArgumentCaptor<RecordCheckoutOpenedCommand> captor =
                ArgumentCaptor.forClass(RecordCheckoutOpenedCommand.class);
        verify(checkoutPurchaseAttributionService).record(captor.capture());
        assertThat(captor.getValue()).satisfies(command -> {
            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.cartId()).isEqualTo(cartId);
            assertThat(command.checkoutAttemptId()).isEqualTo(attemptId);
            assertThat(command.rail().name()).isEqualTo("NATIVE_CHECKOUT");
            assertThat(command.trigger().name()).isEqualTo("VERIFIED_COMPLETION");
            assertThat(command.embeddedSessionId()).isNull();
        });
    }

    @Test
    void updateCheckoutRefreshesLineIdentityBeforeSendingBuyerAndFulfillmentDestination() {
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/stored-checkout");
        cart.replaceCheckoutSession(
                "gid://shopify/Checkout/stored",
                "incomplete",
                "https://merchant.example/stored-checkout",
                null,
                null,
                Instant.parse("2026-06-16T11:07:00Z")
        );
        cartRepository.save(cart);

        CheckoutResult result = cartService.updateCheckout(new UpdateCheckoutCommand(
                cartId,
                USER_ID,
                new UpdateCheckoutCommand.Buyer(
                        "ada@example.com",
                        "Ada",
                        "Lovelace",
                        "+15551234567"
                ),
                new UpdateCheckoutCommand.PostalAddress(
                        "123 Main St",
                        "Apt 4",
                        "Springfield",
                        "IL",
                        "62701",
                        "US"
                ),
                List.of()
        ));

        assertThat(result.checkoutId()).isEqualTo("gid://shopify/Checkout/1");
        assertThat(checkoutDispatchService.updateCount).isEqualTo(1);
        UpdateCheckoutRequest request = checkoutDispatchService.lastUpdateRequest;
        assertThat(request.checkoutId()).isEqualTo("gid://shopify/Checkout/stored");
        assertThat(request.lineItems()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("gid://shopify/CheckoutLine/1");
            assertThat(item.productVariantId()).isEqualTo("gid://shopify/ProductVariant/1");
            assertThat(item.quantity()).isEqualTo(1);
        });
        assertThat(request.buyer().email()).isEqualTo("ada@example.com");
        assertThat(request.buyer().firstName()).isEqualTo("Ada");
        assertThat(request.buyer().lastName()).isEqualTo("Lovelace");
        assertThat(request.buyer().phoneNumber()).isEqualTo("+15551234567");
        assertThat(request.currency()).isNull();
        assertThat(request.context()).isNull();
        assertThat(request.fulfillment().methods()).singleElement().satisfies(method -> {
            assertThat(method.id()).isEqualTo("shipping");
            assertThat(method.type()).isEqualTo("shipping");
            assertThat(method.selectedDestinationId()).isEqualTo("shipping");
            assertThat(method.lineItemIds()).containsExactly("gid://shopify/CheckoutLine/1");
            assertThat(method.destinations()).singleElement().isInstanceOfSatisfying(
                    ShippingDestination.class,
                    destination -> {
                        assertThat(destination.id()).isEqualTo("shipping");
                        assertThat(destination.streetAddress()).isEqualTo("123 Main St");
                        assertThat(destination.extendedAddress()).isEqualTo("Apt 4");
                        assertThat(destination.addressLocality()).isEqualTo("Springfield");
                        assertThat(destination.addressRegion()).isEqualTo("IL");
                        assertThat(destination.postalCode()).isEqualTo("62701");
                        assertThat(destination.addressCountry()).isEqualTo("US");
                        assertThat(destination.firstName()).isEqualTo("Ada");
                        assertThat(destination.lastName()).isEqualTo("Lovelace");
                        assertThat(destination.phoneNumber()).isEqualTo("+15551234567");
                    });
        });
        assertThat(checkoutDispatchService.getCount).isEqualTo(2);
        assertThat(checkoutDispatchService.lastCheckoutId).isEqualTo("gid://shopify/Checkout/stored");
        assertThat(checkoutDispatchService.calls).startsWith("get", "update");
        assertNoInventoryAttribution();
    }

    @Test
    void cartLifecycleCreateUpdateCancelThenGetReturnsNotFound() {
        CartResult created = cartService.create(new CreateCartCommand(
                USER_ID,
                merchant.getId(),
                null,
                List.of(new CreateCartCommand.AddItem("gid://shopify/ProductVariant/1", 1)),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));

        cartService.update(new UpdateCartCommand(
                created.cartId(),
                USER_ID,
                List.of(new UpdateCartCommand.AddItem("gid://shopify/ProductVariant/2", 1)),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        ));
        cartService.cancel(new CancelCartCommand(created.cartId(), USER_ID));

        assertThat(cartDispatchService.createCount).isEqualTo(1);
        assertThat(cartDispatchService.updateCount).isEqualTo(1);
        assertThat(cartDispatchService.cancelCount).isEqualTo(1);
        assertThat(cartDispatchService.lastCanceledRemoteCartId).isEqualTo("gid://shopify/Cart/1");
        assertThat(cartDispatchService.lastCancelIdempotencyKey).isEqualTo(created.cartId());
        assertThat(cartRepository.carts.get(created.cartId()).isActive()).isFalse();
        assertThatThrownBy(() -> cartService.get(new GetCartQuery(created.cartId(), USER_ID, false)))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + created.cartId())
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getWithDifferentUserReturnsNotFoundWithoutRemoteRefresh() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.get(new GetCartQuery(cartId, OTHER_USER_ID, true)))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartDispatchService.getCount).isZero();
        assertThat(checkoutDispatchService.createCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void updateWithDifferentUserReturnsNotFoundWithoutRemoteUpdate() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.update(new UpdateCartCommand(
                cartId,
                OTHER_USER_ID,
                List.of(new UpdateCartCommand.AddItem("gid://shopify/ProductVariant/2", 1)),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        )))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartDispatchService.updateCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void checkoutWithDifferentUserReturnsNotFoundWithoutRemoteRefresh() {
        UUID cartId = UUID.randomUUID();
        cartRepository.save(cart(cartId, "https://merchant.example/checkout"));

        assertThatThrownBy(() -> cartService.checkout(new GetCheckoutQuery(cartId, OTHER_USER_ID, true)))
                .isInstanceOf(CartException.class)
                .hasMessage("Cart not found: " + cartId)
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(cartDispatchService.getCount).isZero();
        assertThat(merchantRepository.findByIdCount).isZero();
    }

    @Test
    void checkoutHandoffSaveWithMissingCartReturnsNotFound() {
        assertThatThrownBy(() -> cartPersistenceService.saveCheckoutHandoff(
                UUID.randomUUID(),
                USER_ID,
                0,
                checkoutDispatchService.checkoutToolResult
        ))
                .isInstanceOf(CartException.class)
                .hasMessageStartingWith("Cart not found:")
                .satisfies(exception -> assertThat(((CartException) exception).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void replaceLinesUpdatesExistingLineForMatchingRemoteLineId() {
        UUID cartId = UUID.randomUUID();
        UUID cartLineId = UUID.randomUUID();
        Cart cart = cart(cartId, "https://merchant.example/checkout", cartLineId);
        CartLine replacementLine = CartLine.builder()
                .remoteCartLineId("gid://shopify/CartLine/1")
                .productId("gid://shopify/Product/2")
                .productTitle("Updated Candle")
                .productVariantId("gid://shopify/ProductVariant/2")
                .variantTitle("Updated")
                .quantity(3)
                .totalAmount("44.85")
                .subtotalAmount("44.85")
                .currency("USD")
                .rawLineResponse("{\"updated\":true}")
                .createdAt(Instant.parse("2026-06-16T11:06:00Z"))
                .updatedAt(Instant.parse("2026-06-16T11:06:00Z"))
                .build();

        cart.replaceLines(List.of(replacementLine));

        assertThat(cart.getLines()).hasSize(1);
        assertThat(cart.getLines().getFirst().getId()).isEqualTo(cartLineId);
        assertThat(cart.getLines().getFirst().getProductTitle()).isEqualTo("Updated Candle");
        assertThat(cart.getLines().getFirst().getQuantity()).isEqualTo(3);
    }

    private Merchant merchant() {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        return Merchant.builder()
                .id(UUID.randomUUID())
                .domain("merchant.example")
                .ucpUrl("https://merchant.example/.well-known/ucp.json")
                .ucpVersion("1.0")
                .advertisedMcpEndpoint("https://merchant.example/api/mcp")
                .profileHash("hash")
                .name("Merchant")
                .description("Description")
                .about("About")
                .targetAudience("Customers")
                .profileQuestion("Question")
                .profileAnswerRaw("Answer")
                .active(true)
                .lastProfiledAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Cart cart(UUID cartId, String checkoutUrl) {
        return cart(cartId, checkoutUrl, (String) null);
    }

    private Cart cart(UUID cartId, String checkoutUrl, String continueUrl) {
        return cart(cartId, checkoutUrl, continueUrl, UUID.randomUUID());
    }

    private Cart cart(UUID cartId, String checkoutUrl, UUID cartLineId) {
        return cart(cartId, checkoutUrl, null, cartLineId, "{}");
    }

    private Cart cart(UUID cartId, String checkoutUrl, String continueUrl, UUID cartLineId) {
        return cart(cartId, checkoutUrl, continueUrl, cartLineId, "{}");
    }

    private Cart cart(UUID cartId, String checkoutUrl, UUID cartLineId, String rawCartResponse) {
        return cart(cartId, checkoutUrl, null, cartLineId, rawCartResponse);
    }

    private Cart cart(
            UUID cartId,
            String checkoutUrl,
            String continueUrl,
            UUID cartLineId,
            String rawCartResponse
    ) {
        Instant now = Instant.parse("2026-06-16T11:05:00Z");
        CartLine line = CartLine.builder()
                .id(cartLineId)
                .remoteCartLineId("gid://shopify/CartLine/1")
                .productId("gid://shopify/Product/1")
                .productTitle("Candle")
                .productVariantId("gid://shopify/ProductVariant/1")
                .variantTitle("3x6")
                .quantity(1)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Cart.builder()
                .id(cartId)
                .userId(USER_ID)
                .merchantId(merchant.getId())
                .merchantDomain(merchant.getDomain())
                .endpoint("https://merchant.example/api/mcp")
                .remoteCartId("gid://shopify/Cart/1")
                .remoteCartIdHash("hash")
                .checkoutUrl(checkoutUrl)
                .continueUrl(continueUrl)
                .rawCartResponse(rawCartResponse)
                .totalQuantity(1)
                .active(true)
                .remoteCreatedAt(CART_REMOTE_CREATED_AT)
                .remoteUpdatedAt(CART_REMOTE_UPDATED_AT)
                .createdAt(now)
                .updatedAt(now)
                .refreshedAt(now)
                .lines(new ArrayList<>(List.of(line)))
                .build();
    }

    private String storedCheckoutResponse() {
        return """
                {
                  "checkout": {
                    "id": "gid://shopify/Checkout/stored",
                    "line_items": [
                      {
                        "id": "gid://shopify/CheckoutLine/1",
                        "item": {
                          "id": "gid://shopify/ProductVariant/1"
                        },
                        "quantity": 1
                      }
                    ]
                  },
                  "errors": []
                }
                """;
    }

    private void assertNoInventoryAttribution() {
        verifyNoInteractions(checkoutPurchaseAttributionService);
    }

    private UcpCartToolResult cartToolResult() {
        return cartToolResult(List.of(cartLine()), 1, List.of());
    }

    private UcpCartToolResult cartToolResult(List<UcpCartResponse.Line> lines, Integer totalQuantity) {
        return cartToolResult(lines, totalQuantity, List.of());
    }

    private UcpCartToolResult cartToolResult(
            List<UcpCartResponse.Line> lines,
            Integer totalQuantity,
            List<UcpCartResponse.DeliveryGroup> deliveryGroups
    ) {
        UcpCartResponse response = cartToolResponse(lines, totalQuantity, deliveryGroups);
        return new UcpCartToolResult(
                "https://merchant.example/api/mcp",
                raw(response),
                response
        );
    }

    private UcpCartResponse cartToolResponse(
            List<UcpCartResponse.Line> lines,
            Integer totalQuantity,
            List<UcpCartResponse.DeliveryGroup> deliveryGroups
    ) {
        return new UcpCartResponse(
                "Checkout when ready",
                new UcpCartResponse.Cart(
                        "gid://shopify/Cart/1",
                        CART_REMOTE_CREATED_AT,
                        CART_REMOTE_UPDATED_AT,
                        null,
                        lines,
                        new UcpCartResponse.Cost(
                                new UcpCartResponse.Money("14.95", "USD"),
                                new UcpCartResponse.Money("14.95", "USD")
                        ),
                        totalQuantity,
                        "https://merchant.example/checkout",
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        deliveryGroups,
                        List.of()
                ),
                List.of(),
                List.of()
        );
    }

    private UcpCartToolResult cartToolResultWithAppliedCodes() {
        UcpCartResponse response = new UcpCartResponse(
                "Checkout when ready",
                new UcpCartResponse.Cart(
                        "gid://shopify/Cart/1",
                        CART_REMOTE_CREATED_AT,
                        CART_REMOTE_UPDATED_AT,
                        null,
                        List.of(cartLine()),
                        new UcpCartResponse.Cost(
                                new UcpCartResponse.Money("7.95", "USD"),
                                new UcpCartResponse.Money("14.95", "USD")
                        ),
                        1,
                        "https://merchant.example/checkout",
                        null,
                        List.of(new UcpCartResponse.AppliedCode(
                                "SAVE5",
                                "Spring discount",
                                true,
                                new UcpCartResponse.Money("5.00", "USD")
                        )),
                        List.of(),
                        List.of(),
                        List.of(new UcpCartResponse.AppliedCode(
                                "1234",
                                "Gift card",
                                true,
                                new UcpCartResponse.Money("2.00", "USD")
                        )),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of()
        );
        return new UcpCartToolResult(
                "https://merchant.example/api/mcp",
                raw(response),
                response
        );
    }

    private UcpCartResponse.Line cartLine() {
        return new UcpCartResponse.Line(
                "gid://shopify/CartLine/1",
                1,
                new UcpCartResponse.Cost(
                        new UcpCartResponse.Money("14.95", "USD"),
                        new UcpCartResponse.Money("14.95", "USD")
                ),
                new UcpCartResponse.Merchandise(
                        "gid://shopify/ProductVariant/1",
                        "3x6",
                        new UcpCartResponse.Product("gid://shopify/Product/1", "Candle")
                )
        );
    }

    private UcpCartResponse.DeliveryGroup deliveryGroup() {
        UcpCartResponse.DeliveryOption standard = deliveryOption("standard", true);
        UcpCartResponse.DeliveryOption express = deliveryOption("express", false);
        return new UcpCartResponse.DeliveryGroup(
                "delivery-group-1",
                "delivery-group-handle-1",
                List.of(standard, express),
                standard
        );
    }

    private UcpCartResponse.DeliveryOption deliveryOption(String handle, boolean selected) {
        String title = handle.equals("standard") ? "Standard" : "Express";
        String speed = handle.equals("standard") ? "3 to 5 business days" : "1 to 2 business days";
        String cost = handle.equals("standard") ? "5.00" : "12.00";
        return new UcpCartResponse.DeliveryOption(
                handle,
                title,
                "Arrives in " + speed,
                null,
                new UcpCartResponse.Money(cost, "USD"),
                null,
                "shipping",
                speed,
                null,
                null,
                selected
        );
    }

    private String raw(UcpCartResponse response) {
        try {
            return new ObjectMapper().writeValueAsString(response);
        } catch (JacksonException exception) {
            throw new AssertionError(exception);
        }
    }

    static class FakeCartDispatchService extends MerchantCartPluginDispatchService {

        private UcpCartToolResult cartToolResult;
        private CreateCartRequest lastCreateRequest;
        private CartToolCallContext lastCallContext;
        private UpdateCartRequest lastUpdateRequest;
        private RuntimeException updateException;
        private String lastRemoteCartId;
        private String lastCanceledRemoteCartId;
        private UUID lastCancelIdempotencyKey;
        private int createCount;
        private int updateCount;
        private int getCount;
        private int cancelCount;

        FakeCartDispatchService() {
            super(mock(com.meant.api.module.merchant.service.MerchantMcpToolClient.class),
                    mock(com.meant.api.plugin.transport.registry.CapabilityRegistry.class),
                    new ObjectMapper(), List.of(),
                    new CartBindingMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        }

        @Override
        public UcpCartToolResult createCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                CreateCartRequest request, UcpSession session) {
            return createCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult createCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                CreateCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext = callContext;
            return createCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult updateCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                UpdateCartRequest request, UcpSession session) {
            return updateCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult updateCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                UpdateCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext = callContext;
            return updateCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult getCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                GetCartRequest request, UcpSession session) {
            return getCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult getCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                GetCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext = callContext;
            return getCart(target.merchantProvider(), request, session);
        }

        @Override
        public CancelCartResponse cancelCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                CancelCartRequest request, UcpSession session, UUID idempotencyKey) {
            lastCancelIdempotencyKey = idempotencyKey;
            return cancelCart(target.merchantProvider(), request, session);
        }

        @Override
        public CancelCartResponse cancelCart(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                CancelCartRequest request,
                UcpSession session,
                CartToolCallContext callContext
        ) {
            lastCallContext = callContext;
            lastCancelIdempotencyKey = callContext.idempotencyKey();
            return cancelCart(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCartToolResult createCart(
                MerchantCartProvider provider,
                CreateCartRequest request,
                UcpSession session
        ) {
            createCount++;
            lastCreateRequest = request;
            return cartToolResult;
        }

        @Override
        public UcpCartToolResult updateCart(
                MerchantCartProvider provider,
                UpdateCartRequest request,
                UcpSession session
        ) {
            updateCount++;
            lastUpdateRequest = request;
            if (updateException != null) {
                throw updateException;
            }
            return cartToolResult;
        }

        @Override
        public UcpCartToolResult getCart(
                MerchantCartProvider provider,
                GetCartRequest request,
                UcpSession session
        ) {
            getCount++;
            lastRemoteCartId = request.cartId();
            return cartToolResult;
        }

        @Override
        public CancelCartResponse cancelCart(
                MerchantCartProvider provider,
                CancelCartRequest request,
                UcpSession session
        ) {
            cancelCount++;
            lastCanceledRemoteCartId = request.cartId();
            return new CancelCartResponse(request.cartId(), "canceled", true, List.of(), List.of());
        }
    }

    static class FakeCheckoutDispatchService extends MerchantCheckoutPluginDispatchService {

        private UcpCheckoutToolResult checkoutToolResult;
        private String lastRemoteCartId;
        private String lastCheckoutId;
        private UpdateCheckoutRequest lastUpdateRequest;
        private CheckoutToolCallContext lastCallContext;
        private int createCount;
        private int getCount;
        private int updateCount;
        private CountDownLatch concurrentCreates;
        private final List<String> calls = new ArrayList<>();

        FakeCheckoutDispatchService() {
            super(mock(com.meant.api.module.merchant.service.MerchantMcpToolClient.class),
                    mock(com.meant.api.plugin.transport.registry.CapabilityRegistry.class),
                    new ObjectMapper(), List.of());
            checkoutToolResult = checkoutToolResult("gid://shopify/Cart/1");
        }

        @Override
        public UcpCheckoutToolResult createCheckout(
                MerchantCartProvider provider,
                CreateCheckoutRequest request,
                UcpSession session
        ) {
            synchronized (this) {
                createCount++;
            }
            lastRemoteCartId = request.cartId();
            if (concurrentCreates != null) {
                concurrentCreates.countDown();
                try {
                    if (!concurrentCreates.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Concurrent checkout requests did not reach the provider together");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while coordinating concurrent checkout requests", exception);
                }
            }
            return checkoutToolResult;
        }

        @Override
        public UcpCheckoutToolResult createCheckout(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                CreateCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext = context;
            return createCheckout(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCheckoutToolResult getCheckout(
                MerchantCartProvider provider,
                GetCheckoutRequest request,
                UcpSession session
        ) {
            getCount++;
            calls.add("get");
            lastCheckoutId = request.checkoutId();
            return checkoutToolResult;
        }

        @Override
        public UcpCheckoutToolResult getCheckout(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                GetCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext = context;
            return getCheckout(target.merchantProvider(), request, session);
        }

        @Override
        public UcpCheckoutToolResult updateCheckout(
                MerchantCartProvider provider,
                UpdateCheckoutRequest request,
                UcpSession session
        ) {
            updateCount++;
            calls.add("update");
            lastUpdateRequest = request;
            return checkoutToolResult;
        }

        @Override
        public UcpCheckoutToolResult updateCheckout(
                com.meant.api.module.cart.service.dto.CartRoutingTarget target,
                UpdateCheckoutRequest request,
                UcpSession session,
                CheckoutToolCallContext context
        ) {
            lastCallContext = context;
            return updateCheckout(target.merchantProvider(), request, session);
        }

        private UcpCheckoutToolResult checkoutToolResult(String cartId) {
            UcpCheckoutResponse response = new UcpCheckoutResponse(
                    null,
                    "Open checkout in browser",
                    new UcpCheckoutResponse.Checkout(
                            "gid://shopify/Checkout/1",
                            cartId,
                            "open",
                            "https://merchant.example/checkout",
                            "https://merchant.example/continue",
                            null,
                            null,
                            Instant.parse("2026-06-16T11:06:00Z"),
                            Instant.parse("2026-06-16T11:06:01Z"),
                            null,
                            null,
                            List.of(new UcpCheckoutResponse.CheckoutLineItem(
                                    "gid://shopify/CheckoutLine/1",
                                    null,
                                    "gid://shopify/ProductVariant/1",
                                    null,
                                    null,
                                    1,
                                    null,
                                    null,
                                    null,
                                    List.of(),
                                    null
                            )),
                            null,
                            new UcpCheckoutResponse.CheckoutBuyer(null, null, "ada@example.com", null),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            List.of()
                    ),
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
                    List.of()
            );
            return new UcpCheckoutToolResult(
                    "https://merchant.example/api/mcp",
                    "{}",
                    response
            );
        }
    }

    static UserSettingsLocationRepository userSettingsLocationRepositoryProxy() {
        return (UserSettingsLocationRepository) Proxy.newProxyInstance(
                UserSettingsLocationRepository.class.getClassLoader(),
                new Class<?>[]{UserSettingsLocationRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdUserIdOrderByDisplayOrderAsc" -> List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    static MerchantCapabilityRepository merchantCapabilityRepositoryProxy() {
        return (MerchantCapabilityRepository) Proxy.newProxyInstance(
                MerchantCapabilityRepository.class.getClassLoader(),
                new Class<?>[]{MerchantCapabilityRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByMerchantIdAndName" -> false;
                    case "findNamesByMerchantId" -> java.util.Set.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    static MerchantIntegrationRepository merchantIntegrationRepositoryProxy() {
        return (MerchantIntegrationRepository) Proxy.newProxyInstance(
                MerchantIntegrationRepository.class.getClassLoader(),
                new Class<?>[]{MerchantIntegrationRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByMerchantIdOrderByCreatedAtAsc" -> List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    static class FakeMerchantRepository {

        private final Map<UUID, Merchant> merchantsById = new HashMap<>();
        private final Map<String, Merchant> merchantsByDomain = new HashMap<>();
        private int findByIdCount;

        void save(Merchant merchant) {
            merchantsById.put(merchant.getId(), merchant);
            merchantsByDomain.put(merchant.getDomain(), merchant);
        }

        MerchantRepository proxy() {
            return (MerchantRepository) Proxy.newProxyInstance(
                    MerchantRepository.class.getClassLoader(),
                    new Class<?>[]{MerchantRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findById" -> {
                            findByIdCount++;
                            yield Optional.ofNullable(merchantsById.get(args[0]));
                        }
                        case "findByDomain", "findByDomainAndActiveTrue" ->
                                Optional.ofNullable(merchantsByDomain.get(args[0]));
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }

    static class FakeCartRepository {

        private final Map<UUID, Cart> carts = new HashMap<>();
        private int saveCount;
        private int findWithLinesCount;

        void save(Cart cart) {
            carts.put(cart.getId(), cart);
        }

        CartRepository proxy() {
            return (CartRepository) Proxy.newProxyInstance(
                    CartRepository.class.getClassLoader(),
                    new Class<?>[]{CartRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "findWithLinesByIdAndUserId", "findForCheckoutUpdate" -> {
                            findWithLinesCount++;
                            Cart cart = carts.get(args[0]);
                            yield cart == null || !cart.getUserId().equals(args[1]) || !cart.isActive()
                                    ? Optional.empty()
                                    : Optional.of(cart);
                        }
                        case "save" -> {
                            Cart cart = (Cart) args[0];
                            save(cart);
                            saveCount++;
                            yield cart;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}

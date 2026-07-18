package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meant.api.module.cart.constant.CartSnapshotPurpose;
import com.meant.api.module.cart.constant.CheckoutNextAction;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.repository.CartRepository;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.OfferIdentity;
import com.meant.api.module.catalog.service.dto.OfferMerchantScope;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.ProviderIdentity;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.ResultProvenance;
import com.meant.api.module.catalog.service.dto.ResultSourceReference;
import com.meant.api.module.catalog.service.dto.ResultSourceType;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.plugin.cart.common.dto.UcpCartResponse;
import com.meant.api.plugin.cart.common.dto.UcpCartToolResult;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutResponse;
import com.meant.api.plugin.checkout.common.dto.UcpCheckoutToolResult;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartPersistenceOfferBindingTest {

    @Test
    void providerReadRefreshPreservesActiveCheckoutLifecycleAndCachedPlanning() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        Cart cart = service.saveSnapshot(null, java.util.UUID.randomUUID(), target(), cartResult("line-1"),
                List.of(), List.of(selectedOffer()), CartSnapshotPurpose.CART_MUTATION);
        Instant synchronizedAt = Instant.parse("2026-07-11T20:00:00Z");
        cart.replaceCheckoutSession(
                "checkout-1", "processing-wire", "https://seller.test/handoff", "https://seller.test/continue",
                null, "2026-04-08", "PROCESSING", synchronizedAt);

        Cart refreshed = service.saveSnapshot(cart, cart.getUserId(), target(), cartResult("line-1"),
                List.of(), List.of(), CartSnapshotPurpose.READ_REFRESH);

        assertThat(refreshed.getCheckoutId()).isEqualTo("checkout-1");
        assertThat(refreshed.getCheckoutStatus()).isEqualTo("processing-wire");
        assertThat(refreshed.getCheckoutLifecycleState()).isEqualTo("PROCESSING");
        assertThat(refreshed.getCheckoutProtocolVersion()).isEqualTo("2026-04-08");
        assertThat(refreshed.getCheckoutSynchronizedAt()).isEqualTo(synchronizedAt);
        assertThat(refreshed.getCheckoutUrl()).isEqualTo("https://seller.test/handoff");
        assertThat(refreshed.getContinueUrl()).isEqualTo("https://seller.test/continue");
        assertThat(new CheckoutResultMapper(new ObjectMapper(), new CheckoutExecutionPlanner())
                .from(refreshed, MerchantExecutionPolicy.unavailable()).nextAction())
                .isEqualTo(CheckoutNextAction.WAIT);
    }

    @Test
    void legacyReadRefreshPreservesPermittedRawCheckoutResponseAndHandoff() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        MerchantCartProvider provider = new MerchantCartProvider(
                null, "seller.test", "https://seller.test/api/ucp/mcp", null,
                List.of(), MerchantExecutionPolicy.unavailable());
        Cart cart = service.saveSnapshot((Cart) null, java.util.UUID.randomUUID(), provider, cartResult("line-1"),
                List.of(), CartSnapshotPurpose.CART_MUTATION);
        Instant synchronizedAt = Instant.parse("2026-07-11T20:00:00Z");
        cart.replaceCheckoutSession(
                "legacy-checkout", "incomplete", "https://seller.test/handoff", "https://seller.test/continue",
                "{\"legacy\":\"permitted\"}", "2026-04-08", "INCOMPLETE", synchronizedAt);

        Cart refreshed = service.saveSnapshot(cart, cart.getUserId(), provider, cartResult("line-1"),
                List.of(), CartSnapshotPurpose.READ_REFRESH);

        assertThat(refreshed.getCheckoutId()).isEqualTo("legacy-checkout");
        assertThat(refreshed.getRawCheckoutResponse()).isEqualTo("{\"legacy\":\"permitted\"}");
        assertThat(refreshed.getCheckoutSynchronizedAt()).isEqualTo(synchronizedAt);
        assertThat(refreshed.getCheckoutUrl()).isEqualTo("https://seller.test/handoff");
        assertThat(refreshed.getContinueUrl()).isEqualTo("https://seller.test/continue");
    }

    @Test
    void cartMutationSnapshotInvalidatesCheckoutSoItCannotBeReused() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        Cart cart = service.saveSnapshot(null, java.util.UUID.randomUUID(), target(), cartResult("line-1"),
                List.of(), List.of(selectedOffer()), CartSnapshotPurpose.CART_MUTATION);
        cart.replaceCheckoutSession(
                "stale-checkout", "incomplete", "https://seller.test/handoff", "https://seller.test/continue",
                null, "2026-04-08", "INCOMPLETE", Instant.now());

        Cart mutated = service.saveSnapshot(cart, cart.getUserId(), target(), cartResult("line-rotated"),
                List.of(), List.of(), CartSnapshotPurpose.CART_MUTATION);

        assertThat(mutated.getCheckoutId()).isNull();
        assertThat(mutated.getCheckoutStatus()).isNull();
        assertThat(mutated.getCheckoutLifecycleState()).isNull();
        assertThat(mutated.getCheckoutProtocolVersion()).isNull();
        assertThat(mutated.getCheckoutSynchronizedAt()).isNull();
        assertThat(mutated.getCheckoutUrl()).isNull();
        assertThat(mutated.getContinueUrl()).isNull();
        assertThat(mutated.getRawCheckoutResponse()).isNull();
    }

    @Test
    void persistsExactIdentityButNoDisplayPriceCheckoutUrlOrRawProviderPayload() {
        CartRepository repository = savingRepository();
        CartPersistenceService service = new CartPersistenceService(repository, new ObjectMapper());
        ResolvedSelectedOffer selected = selectedOffer();
        CartRoutingTarget target = new CartRoutingTarget(
                "SHOPIFY:merchant:shop-1", MerchantIntegrationProvider.SHOPIFY, null, "shop-1",
                new MerchantCartProvider(null, "seller.test", "https://seller.test/api/ucp/mcp", null,
                        List.of(), MerchantExecutionPolicy.unavailable()));
        UcpCartResponse.Money money = new UcpCartResponse.Money("99.00", "USD");
        UcpCartResponse.Line line = new UcpCartResponse.Line(
                "line-1", 1, new UcpCartResponse.Cost(money, money),
                new UcpCartResponse.Merchandise(
                        "variant-1", "Secret display variant",
                        new UcpCartResponse.Product("provider-product", "Secret display product")));
        UcpCartResponse.Cart remote = new UcpCartResponse.Cart(
                "remote-cart", Instant.now(), Instant.now(), Instant.now().plusSeconds(600), List.of(line),
                new UcpCartResponse.Cost(money, money), 1, "https://seller.test/checkout", null,
                List.of(new UcpCartResponse.AppliedCode(
                        "DISCOUNT-SECRET", "Secret label", true, new UcpCartResponse.Money("5", "USD"))),
                List.of(), List.of(), List.of(new UcpCartResponse.AppliedCode(
                        "GIFT-CARD-SECRET", "Gift card", true, new UcpCartResponse.Money("5", "USD"))),
                List.of(), List.of(), List.of());
        UcpCartToolResult result = new UcpCartToolResult(
                "https://catalog.shopify.com/api/ucp/mcp", "{\"raw\":\"prohibited\"}",
                new UcpCartResponse("provider instructions", remote, List.of(), List.of()));

        Cart cart = service.saveSnapshot(null, java.util.UUID.randomUUID(), target, result, List.of(),
                List.of(selected), CartSnapshotPurpose.CART_MUTATION);
        var currentResult = new CartResultMapper(new ObjectMapper()).from(cart, result.response());

        assertThat(cart.getRawCartResponse()).isEqualTo("{}");
        assertThat(cart.getCheckoutUrl()).isNull();
        assertThat(cart.getTotalAmount()).isNull();
        assertThat(cart.getMerchantId()).isNull();
        assertThat(cart.getAppliedCodes()).isEmpty();
        assertThat(cart.getRoutingScopeKey()).isEqualTo("SHOPIFY:merchant:shop-1");
        assertThat(currentResult.checkoutUrl()).isEqualTo("https://seller.test/checkout");
        assertThat(currentResult.totalAmount()).isEqualTo("99.00");
        assertThat(currentResult.currency()).isEqualTo("USD");
        assertThat(currentResult.lines()).singleElement().satisfies(current -> {
            assertThat(current.productTitle()).isEqualTo("Secret display product");
            assertThat(current.totalAmount()).isEqualTo("99.00");
        });
        assertThat(cart.getLines()).singleElement().satisfies(saved -> {
            assertThat(saved.getOfferKey()).isEqualTo(selected.offerKey());
            assertThat(saved.getExternalProductId()).isEqualTo("discovery-product");
            assertThat(saved.getOfferProductId()).isEqualTo("offer-product");
            assertThat(saved.getExternalVariantId()).isEqualTo("variant-1");
            assertThat(saved.getSelectedOptionsJson()).contains("Color").contains("Black");
            assertThat(saved.getComponentsJson()).contains("component-product");
            assertThat(saved.getSellingPlanJson()).contains("plan-1");
            assertThat(saved.getRawLineResponse()).isEqualTo("{}");
            assertThat(saved.getProductTitle()).isNull();
            assertThat(saved.getTotalAmount()).isNull();
        });
    }

    @Test
    void persistsVerifiedProductPageUrlInsteadOfCatalogSourceEndpoint() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        ResolvedSelectedOffer base = selectedOffer();
        ResultFreshness freshness = new ResultFreshness(Instant.now(), Instant.now().plusSeconds(300));
        RehydratedCommercialFacts facts = new RehydratedCommercialFacts(
                "Trail Shoe",
                "Seller",
                URI.create("https://seller.test/products/trail-shoe"),
                null,
                OfferAvailability.unknown(),
                base.identity().externalVariantIdentity(),
                base.identity().selectedOptions(),
                List.of(),
                List.of(),
                freshness,
                CommercialFactsFreshness.fromSingleObservation(freshness)
        );
        ResolvedSelectedOffer selected = new ResolvedSelectedOffer(
                base.canonicalProductKey(), base.offerKey(), base.identity(), base.provenance(),
                base.rehydratedReference(), facts);

        Cart cart = service.saveSnapshot(
                null,
                java.util.UUID.randomUUID(),
                target(),
                cartResult("line-1"),
                List.of(),
                List.of(selected),
                CartSnapshotPurpose.CART_MUTATION
        );

        assertThat(cart.getLines()).singleElement().satisfies(line ->
                assertThat(line.getProductUrl()).isEqualTo("https://seller.test/products/trail-shoe"));
    }

    @Test
    void boundRemoteCartHashIncludesImmutableRoutingScope() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        Cart first = service.saveSnapshot(null, java.util.UUID.randomUUID(), target(), cartResult("line-1"),
                List.of(), List.of(selectedOffer()), CartSnapshotPurpose.CART_MUTATION);
        CartRoutingTarget other = new CartRoutingTarget(
                "SHOPIFY:merchant:shop-2", MerchantIntegrationProvider.SHOPIFY, null, "shop-2",
                new MerchantCartProvider(null, "other.test", "https://other.test/api/ucp/mcp", null,
                        List.of(), MerchantExecutionPolicy.unavailable()));
        ResolvedSelectedOffer selected = selectedOffer("shop-2");
        Cart second = service.saveSnapshot(null, java.util.UUID.randomUUID(), other, cartResult("line-1"),
                List.of(), List.of(selected), CartSnapshotPurpose.CART_MUTATION);

        assertThat(first.getRemoteCartId()).isEqualTo(second.getRemoteCartId());
        assertThat(first.getRemoteCartIdHash()).isNotEqualTo(second.getRemoteCartIdHash());
    }

    @Test
    void preservesImmutableOfferBindingWhenProviderRotatesRemoteLineId() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        ResolvedSelectedOffer selected = selectedOffer();
        CartRoutingTarget target = target();

        Cart cart = service.saveSnapshot(
                null, java.util.UUID.randomUUID(), target, cartResult("line-1"), List.of(), List.of(selected),
                CartSnapshotPurpose.CART_MUTATION);
        Cart refreshed = service.saveSnapshot(
                cart, cart.getUserId(), target, cartResult("line-rotated"), List.of(), List.of(),
                CartSnapshotPurpose.READ_REFRESH);

        assertThat(refreshed.getLines()).singleElement().satisfies(saved -> {
            assertThat(saved.getRemoteCartLineId()).isEqualTo("line-rotated");
            assertThat(saved.getOfferKey()).isEqualTo(selected.offerKey());
            assertThat(saved.getExternalProductId()).isEqualTo("discovery-product");
            assertThat(saved.getSelectedOptionsJson()).contains("Color").contains("Black");
        });
    }

    @Test
    void refusesAmbiguousOrCoalescedBindingsForTheSameVariant() {
        CartPersistenceService service = new CartPersistenceService(savingRepository(), new ObjectMapper());
        ResolvedSelectedOffer selected = selectedOffer();

        assertThatThrownBy(() -> service.saveSnapshot(
                null, java.util.UUID.randomUUID(), target(), cartResult("line-1"), List.of(),
                List.of(selected, selected), CartSnapshotPurpose.CART_MUTATION))
                .isInstanceOf(CartException.class)
                .extracting("bindingFailure")
                .isEqualTo(CartException.BindingFailure.IDENTITY_MISMATCH);
    }

    @Test
    void cartRoutingScopeRejectsFieldSubstitutionEvenWhenScopeKeyIsUnchanged() {
        Cart cart = Cart.builder().build();
        cart.assignRoutingScope("SHOPIFY", null, "shop-1", "SHOPIFY:merchant:shop-1",
                null, "seller.test");

        assertThatThrownBy(() -> cart.assignRoutingScope(
                "GENERIC_UCP", null, "shop-2", "SHOPIFY:merchant:shop-1", null, "attacker.test"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shopifyCheckoutPersistsOnlyPermittedLifecycleMetadata() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CartPersistenceService service = new CartPersistenceService(savingRepository(), objectMapper);
        Cart cart = service.saveSnapshot(null, java.util.UUID.randomUUID(), target(), cartResult("line-1"),
                List.of(), List.of(selectedOffer()), CartSnapshotPurpose.CART_MUTATION);
        String raw = """
                {"ucp":{"version":"2026-04-08"},"checkout":{"id":"checkout-1","cart_id":"remote-cart",
                "status":"requires_escalation","continue_url":"https://seller.test/checkout",
                "buyer":{"email":"buyer-secret@example.test"},"payment":{"instruments":[{"token":"secret"}]}}}
                """;
        UcpCheckoutResponse response = objectMapper.readValue(raw, UcpCheckoutResponse.class);

        Cart saved = service.saveCheckoutHandoff(
                cart.getId(), cart.getUserId(), cart.getCheckoutGeneration(), new UcpCheckoutToolResult(
                "https://seller.test/api/ucp/mcp", raw, response));

        assertThat(saved.getCheckoutId()).isEqualTo("checkout-1");
        assertThat(saved.getCheckoutStatus()).isEqualTo("requires_escalation");
        assertThat(saved.getCheckoutLifecycleState()).isEqualTo("REQUIRES_ESCALATION");
        assertThat(saved.getCheckoutProtocolVersion()).isEqualTo("2026-04-08");
        assertThat(saved.getCheckoutSynchronizedAt()).isNotNull();
        assertThat(saved.getContinueUrl()).isEqualTo("https://seller.test/checkout");
        assertThat(saved.getRawCheckoutResponse()).isNull();
        assertThat(saved.toString()).doesNotContain("buyer-secret").doesNotContain("secret");
    }

    private CartRepository savingRepository() {
        java.util.concurrent.atomic.AtomicReference<Cart> stored = new java.util.concurrent.atomic.AtomicReference<>();
        return (CartRepository) Proxy.newProxyInstance(
                CartRepository.class.getClassLoader(),
                new Class<?>[]{CartRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("save")) {
                        stored.set((Cart) arguments[0]);
                        return arguments[0];
                    }
                    if (method.getName().equals("findForCheckoutUpdate")) {
                        Cart cart = stored.get();
                        return cart != null && cart.getId().equals(arguments[0])
                                && cart.getUserId().equals(arguments[1])
                                ? java.util.Optional.of(cart) : java.util.Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private CartRoutingTarget target() {
        return new CartRoutingTarget(
                "SHOPIFY:merchant:shop-1", MerchantIntegrationProvider.SHOPIFY, null, "shop-1",
                new MerchantCartProvider(null, "seller.test", "https://seller.test/api/ucp/mcp", null,
                        List.of(), MerchantExecutionPolicy.unavailable()));
    }

    private UcpCartToolResult cartResult(String lineId) {
        UcpCartResponse.Money money = new UcpCartResponse.Money("99.00", "USD");
        UcpCartResponse.Line line = new UcpCartResponse.Line(
                lineId, 1, new UcpCartResponse.Cost(money, money),
                new UcpCartResponse.Merchandise(
                        "variant-1", "Secret display variant",
                        new UcpCartResponse.Product("provider-product", "Secret display product")));
        UcpCartResponse.Cart remote = new UcpCartResponse.Cart(
                "remote-cart", Instant.now(), Instant.now(), Instant.now().plusSeconds(600), List.of(line),
                new UcpCartResponse.Cost(money, money), 1, "https://seller.test/checkout", null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new UcpCartToolResult(
                "https://catalog.shopify.com/api/ucp/mcp", "{\"raw\":\"prohibited\"}",
                new UcpCartResponse("provider instructions", remote, List.of(), List.of()));
    }

    private ResolvedSelectedOffer selectedOffer() {
        return selectedOffer("shop-1");
    }

    private ResolvedSelectedOffer selectedOffer(String merchantId) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        ExternalIdentifier merchant = id(ExternalIdentifierType.MERCHANT, merchantId);
        List<ProductAttribute> options = List.of(new ProductAttribute("variant", "Color", "Black"));
        OfferIdentity identity = new OfferIdentity(
                provider,
                OfferMerchantScope.external(merchant),
                id(ExternalIdentifierType.PRODUCT, "offer-product"),
                id(ExternalIdentifierType.VARIANT, "variant-1"),
                options,
                List.of(new OfferComponentIdentity(
                        id(ExternalIdentifierType.PRODUCT, "component-product"), null, 2, options)),
                new SellingPlanIdentity(null, id(ExternalIdentifierType.SELLING_PLAN, "plan-1"), List.of()));
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG");
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, merchant, id(ExternalIdentifierType.PRODUCT, "discovery-product"),
                id(ExternalIdentifierType.VARIANT, "variant-1"), new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "catalog", null));
        CatalogProductReference reference = new CatalogProductReference(
                identity.key(), source, null, null, merchant, provenance.externalProductReference(),
                provenance.externalVariantReference(), options);
        return new ResolvedSelectedOffer("canonical", identity.key(), identity, provenance, reference);
    }

    private ExternalIdentifier id(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }
}

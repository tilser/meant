package com.meant.api.module.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.meant.api.module.cart.entity.Cart;
import com.meant.api.module.cart.entity.CartLine;
import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.catalog.service.CatalogProductRehydrationService;
import com.meant.api.module.catalog.service.CatalogProductRehydrationMetrics;
import com.meant.api.module.catalog.service.dto.CatalogProductReference;
import com.meant.api.module.catalog.service.dto.CatalogProductRehydrationResult;
import com.meant.api.module.catalog.service.dto.CommercialFactsFreshness;
import com.meant.api.module.catalog.service.dto.ExternalIdentifier;
import com.meant.api.module.catalog.service.dto.ExternalIdentifierType;
import com.meant.api.module.catalog.service.dto.OfferAvailability;
import com.meant.api.module.catalog.service.dto.OfferAvailabilityStatus;
import com.meant.api.module.catalog.service.dto.OfferComponentIdentity;
import com.meant.api.module.catalog.service.dto.ProductAttribute;
import com.meant.api.module.catalog.service.dto.RehydratedCommercialFacts;
import com.meant.api.module.catalog.service.dto.ResultFreshness;
import com.meant.api.module.catalog.service.dto.CatalogRehydrationContext;
import com.meant.api.module.catalog.service.dto.DiscoverySourceIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanIdentity;
import com.meant.api.module.catalog.service.dto.SellingPlanOption;
import com.meant.api.module.catalog.service.port.CatalogProductRehydrationProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CartOfferRevalidationServiceTest {

    private final StubRehydrationProvider provider = new StubRehydrationProvider();
    private final CatalogProductRehydrationService rehydrationService = new CatalogProductRehydrationService(
            List.of(provider), new CatalogProductRehydrationMetrics(new SimpleMeterRegistry()));
    private final CartOfferRevalidationService service = new CartOfferRevalidationService(
            rehydrationService, new ObjectMapper(), new CartBindingMetrics(new SimpleMeterRegistry()));

    @Test
    void revalidatesAllPersistedLinesInOneBoundedBatch() {
        provider.mode = Mode.FRESH;

        service.revalidate(cart(line("offer-1", "variant-1"), line("offer-2", "variant-2")), null);

        assertThat(provider.calls).isEqualTo(1);
        assertThat(provider.lastBatch).hasSize(2);
    }

    @Test
    void reconstructsPersistedBundleAndSellingPlanForExactRevalidation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OfferComponentIdentity component = new OfferComponentIdentity(
                identifier(ExternalIdentifierType.PRODUCT, "component-product"),
                identifier(ExternalIdentifierType.VARIANT, "component-variant"),
                2,
                List.of(new ProductAttribute("variant-option", "Color", "Blue"))
        );
        SellingPlanIdentity plan = new SellingPlanIdentity(
                identifier(ExternalIdentifierType.SELLING_PLAN_GROUP, "subscription-group"),
                identifier(ExternalIdentifierType.SELLING_PLAN, "monthly-plan"),
                List.of(new SellingPlanOption("frequency", "monthly"))
        );

        service.revalidate(cart(line(
                "offer-configured",
                "variant-1",
                objectMapper.writeValueAsString(List.of(component)),
                objectMapper.writeValueAsString(plan)
        )), null);

        assertThat(provider.lastBatch).singleElement().satisfies(reference -> {
            assertThat(reference.components()).containsExactly(component);
            assertThat(reference.sellingPlanIdentity()).isEqualTo(plan);
        });
    }

    @Test
    void blocksCurrentVariantMismatch() {
        provider.mode = Mode.MISMATCH;

        assertThatThrownBy(() -> service.revalidate(cart(line("offer-1", "variant-1")), null))
                .isInstanceOf(CartException.class)
                .extracting("bindingFailure")
                .isEqualTo(CartException.BindingFailure.IDENTITY_MISMATCH);
    }

    @Test
    void blocksExternalMerchantDomainMismatch() {
        provider.mode = Mode.DOMAIN_MISMATCH;

        Cart cart = cart(line("offer-1", "variant-1"));
        cart.assignProvider(null, "shop.example");

        assertThatThrownBy(() -> service.revalidate(cart, null))
                .isInstanceOf(CartException.class)
                .extracting("bindingFailure")
                .isEqualTo(CartException.BindingFailure.IDENTITY_MISMATCH);
    }

    @Test
    void blocksUnknownCurrentAvailability() {
        provider.mode = Mode.UNKNOWN;

        assertThatThrownBy(() -> service.revalidate(cart(line("offer-1", "variant-1")), null))
                .isInstanceOf(CartException.class)
                .extracting("bindingFailure")
                .isEqualTo(CartException.BindingFailure.STALE_OR_UNAVAILABLE);
    }

    private Cart cart(CartLine... lines) {
        return Cart.builder().lines(new ArrayList<>(List.of(lines))).build();
    }

    private CartLine line(String offerKey, String variantId) {
        return line(offerKey, variantId, "[]", null);
    }

    private CartLine line(
            String offerKey,
            String variantId,
            String componentsJson,
            String sellingPlanJson
    ) {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        return CartLine.builder()
                .remoteCartLineId("remote-" + variantId)
                .productVariantId(variantId)
                .quantity(1)
                .provider("SHOPIFY")
                .externalMerchantId("shop-1")
                .externalProductId("product-1")
                .externalVariantId(variantId)
                .offerKey(offerKey)
                .sourceType("PROVIDER_CATALOG")
                .sourceIdentity("SHOPIFY_GLOBAL_CATALOG")
                .selectedOptionsJson("[]")
                .componentsJson(componentsJson)
                .sellingPlanJson(sellingPlanJson)
                .rawLineResponse("{}")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private RehydratedCommercialFacts facts(
            CatalogProductReference reference,
            OfferAvailabilityStatus availability
    ) {
        OfferAvailability current = availability == OfferAvailabilityStatus.UNKNOWN
                ? OfferAvailability.unknown()
                : available();
        return new RehydratedCommercialFacts(
                "Product", null, current, reference.externalVariantReference(), reference.selectedOptions(),
                List.of(), List.of(), freshness(), CommercialFactsFreshness.fromSingleObservation(freshness()));
    }

    private ExternalIdentifier variant(String value) {
        return identifier(ExternalIdentifierType.VARIANT, value);
    }

    private ExternalIdentifier identifier(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }

    private OfferAvailability available() {
        return new OfferAvailability(OfferAvailabilityStatus.IN_STOCK, 1, null);
    }

    private ResultFreshness freshness() {
        return new ResultFreshness(
                Instant.parse("2026-07-11T10:00:00Z"), Instant.parse("2026-07-11T10:02:00Z"));
    }

    private enum Mode { FRESH, MISMATCH, DOMAIN_MISMATCH, UNKNOWN }

    private final class StubRehydrationProvider implements CatalogProductRehydrationProvider {
        private Mode mode = Mode.FRESH;
        private int calls;
        private List<CatalogProductReference> lastBatch = List.of();

        @Override
        public boolean supports(DiscoverySourceIdentity source) {
            return true;
        }

        @Override
        public List<CatalogProductRehydrationResult> rehydrate(
                List<CatalogProductReference> references,
                CatalogRehydrationContext context
        ) {
            calls++;
            lastBatch = List.copyOf(references);
            return references.stream().map(reference -> {
                RehydratedCommercialFacts currentFacts = switch (mode) {
                    case FRESH -> facts(reference, OfferAvailabilityStatus.IN_STOCK);
                    case UNKNOWN -> facts(reference, OfferAvailabilityStatus.UNKNOWN);
                    case MISMATCH -> new RehydratedCommercialFacts(
                            "Product", null, available(), variant("different-variant"), List.of(),
                            List.of(), List.of(), freshness(),
                            CommercialFactsFreshness.fromSingleObservation(freshness()));
                    case DOMAIN_MISMATCH -> facts(reference, OfferAvailabilityStatus.IN_STOCK);
                };
                CatalogProductReference resolved = mode == Mode.DOMAIN_MISMATCH
                        ? new CatalogProductReference(
                                reference.interactionKey(), reference.discoverySource(), reference.localMerchantId(),
                                reference.localRouting(), reference.externalMerchantReference(), "attacker.example",
                                reference.externalProductReference(), reference.externalVariantReference(),
                                reference.selectedOptions())
                        : reference;
                return CatalogProductRehydrationResult.fresh(reference, resolved, currentFacts);
            }).toList();
        }
    }
}

package com.meant.api.provider.shopify.checkout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.exception.CartException;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.checkout.service.dto.CheckoutToolCallContext;
import com.meant.api.module.merchant.constant.CapabilityAvailability;
import com.meant.api.module.merchant.constant.CapabilityIntegrationHealth;
import com.meant.api.module.merchant.constant.CommerceExecutionRail;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.auth.ShopifyMerchantUcpTransport;
import com.meant.api.provider.shopify.capability.ShopifyAuthorizationTier;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessProperties;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import com.meant.api.provider.shopify.cart.ShopifyExternalOfferCartRoutingProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ShopifyCheckoutToolTransportTest {
    private final ShopifyMerchantUcpTransport merchant = mock(ShopifyMerchantUcpTransport.class);
    private final ShopifyExternalOfferCartRoutingProvider external = mock(ShopifyExternalOfferCartRoutingProvider.class);
    private final ShopifyAgentAuthProperties auth = mock(ShopifyAgentAuthProperties.class);
    private final ShopifyCapabilityReadinessProperties readiness =
            mock(ShopifyCapabilityReadinessProperties.class);
    private final ShopifyCheckoutToolTransport transport;

    ShopifyCheckoutToolTransportTest() {
        when(auth.isEnabled()).thenReturn(true);
        when(readiness.authorizationTier()).thenReturn(ShopifyAuthorizationTier.TOKEN);
        transport = new ShopifyCheckoutToolTransport(
                merchant, external,
                new ShopifyCartProperties(Duration.ofHours(1), Duration.ofSeconds(1), Duration.ofSeconds(2),
                        Duration.ofSeconds(3), Duration.ofSeconds(2)),
                auth, readiness);
    }

    @Test
    void managedCheckoutRequiresAvailableDecisionForTheSameImmutableIntegration() {
        UUID integrationA = UUID.randomUUID();
        UUID integrationB = UUID.randomUUID();

        assertBlocked(managed(integrationA, decision(integrationB, true)));
        assertBlocked(managed(integrationA, decision(integrationA, false)));
        verifyNoInteractions(merchant, external);
    }

    @Test
    void managedReadyCheckoutUsesExactTargetAndStableIdempotencyMetadata() {
        UUID integration = UUID.randomUUID();
        CartRoutingTarget target = managed(integration, decision(integration, true));
        UUID key = UUID.randomUUID();
        when(merchant.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://shop.test/api/ucp/mcp", "{}", new ObjectMapper().createObjectNode(),
                        NegotiatedCapabilities.none()));

        transport.call(target, "cancel_checkout", Map.of(),
                new CheckoutToolCallContext(key, true, "203.0.113.42"));

        verify(merchant).call(eq(target), eq(CommerceOperation.CHECKOUT_SESSION), eq("cancel_checkout"),
                any(), eq(Map.of(
                        "Idempotency-Key", key.toString(),
                        ShopifyCheckoutRequestHeaderContributor.BUYER_IP_HEADER, "203.0.113.42"
                )), eq(true));
        verifyNoInteractions(external);
    }

    @Test
    void externalMissingCheckoutCapabilityFailsBeforeCheckoutIo() {
        CartRoutingTarget target = new CartRoutingTarget(
                "SHOPIFY:merchant:shop-1", MerchantIntegrationProvider.SHOPIFY, null, "shop-1",
                new MerchantCartProvider(null, "shop.test", "https://shop.test/api/ucp/mcp", null,
                        List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(),
                        Set.of("dev.ucp.shopping.cart")));
        when(external.refreshForCheckout(target)).thenThrow(new IllegalStateException("missing checkout"));

        assertThatThrownBy(() -> transport.call(
                target, "create_checkout", Map.of(), CheckoutToolCallContext.standard()))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(merchant);
    }

    @Test
    void freshExternalCheckoutCapabilityExecutesOnTheSameImmutableAuthority() {
        CommerceCapabilityDecision checkout = decision(null, true);
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(Arrays.stream(CommerceOperation.values())
                .map(operation -> operation == CommerceOperation.CHECKOUT_SESSION
                        ? checkout : MerchantExecutionPolicy.unavailable().decision(operation)).toList());
        CartRoutingTarget target = new CartRoutingTarget(
                "SHOPIFY:merchant:shop-1", MerchantIntegrationProvider.SHOPIFY, null, "shop-1",
                new MerchantCartProvider(null, "shop.test", "https://shop.test/api/ucp/mcp", null,
                        List.of(), policy, Instant.now(), Set.of("dev.ucp.shopping.checkout")));
        when(merchant.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://shop.test/api/ucp/mcp", "{}", new ObjectMapper().createObjectNode(),
                        NegotiatedCapabilities.none()));

        transport.call(target, "get_checkout", Map.of(), CheckoutToolCallContext.standard());

        verify(merchant).call(eq(target), eq(CommerceOperation.CHECKOUT_SESSION), eq("get_checkout"),
                any(), eq(Map.of()), eq(true));
        verifyNoInteractions(external);
    }

    private void assertBlocked(CartRoutingTarget target) {
        assertThatThrownBy(() -> transport.call(
                target, "create_checkout", Map.of(), CheckoutToolCallContext.standard()))
                .isInstanceOf(CartException.class);
    }

    private CartRoutingTarget managed(UUID integrationId, CommerceCapabilityDecision checkout) {
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(Arrays.stream(CommerceOperation.values())
                .map(operation -> operation == CommerceOperation.CHECKOUT_SESSION
                        ? checkout : MerchantExecutionPolicy.unavailable().decision(operation)).toList());
        return new CartRoutingTarget(
                "SHOPIFY:integration:" + integrationId, MerchantIntegrationProvider.SHOPIFY,
                integrationId, "shop-1", new MerchantCartProvider(
                UUID.randomUUID(), "shop.test", "https://shop.test/api/ucp/mcp", null,
                List.of(), policy, Instant.now(), Set.of("dev.ucp.shopping.checkout")));
    }

    private CommerceCapabilityDecision decision(UUID integrationId, boolean available) {
        return new CommerceCapabilityDecision(
                CommerceOperation.CHECKOUT_SESSION, available,
                available ? CapabilityAuthorizationDecision.ready("TOKEN", "TOKEN", Set.of())
                        : CapabilityAuthorizationDecision.notRequired(),
                true, available ? CapabilityIntegrationHealth.HEALTHY : CapabilityIntegrationHealth.INACTIVE,
                false, available ? CapabilityAvailability.AVAILABLE : CapabilityAvailability.UNAVAILABLE,
                available ? CommerceExecutionRail.PROVIDER_CHECKOUT_SESSION : CommerceExecutionRail.NONE,
                List.of(), integrationId, MerchantIntegrationProvider.SHOPIFY);
    }
}

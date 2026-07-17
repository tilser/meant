package com.meant.api.provider.shopify.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.service.CartBindingMetrics;
import com.meant.api.module.cart.service.dto.CartToolCallContext;
import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.catalog.service.dto.*;
import com.meant.api.module.merchant.properties.MerchantExecutionPolicyProperties;
import com.meant.api.module.merchant.properties.MerchantUcpProfileObservationProperties;
import com.meant.api.module.merchant.service.CapabilityExecutionPolicyEvaluator;
import com.meant.api.module.merchant.service.MerchantCartProviderLookupService;
import com.meant.api.module.merchant.service.MerchantEnrichmentCandidateService;
import com.meant.api.module.merchant.service.MerchantExecutionPolicyService;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.MerchantUcpProfileObservationService;
import com.meant.api.module.merchant.service.UcpProfileClient;
import com.meant.api.module.merchant.service.dto.MerchantMcpToolCallResult;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.CommerceCapabilityDecision;
import com.meant.api.module.merchant.service.dto.CapabilityAuthorizationDecision;
import com.meant.api.module.merchant.constant.*;
import com.meant.api.module.merchant.service.dto.UcpCapabilityDefinition;
import com.meant.api.module.merchant.service.dto.UcpProfile;
import com.meant.api.module.merchant.service.dto.UcpProfileFetchResult;
import com.meant.api.module.merchant.service.dto.UcpServiceDefinition;
import com.meant.api.module.user.service.dto.ResolvedSelectedOffer;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.provider.shopify.auth.ShopifyMerchantUcpTransport;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopifyOfferCartRoutingTest {

    @Test
    void observedCheckoutCapabilityMakesExternalShopifyEmbeddedCheckoutAvailable() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.empty());
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(eq("shop.example"), any())).thenReturn(profile(Map.of(
                "dev.ucp.shopping.cart", List.of(capability("dev.ucp.shopping.cart")),
                "dev.ucp.shopping.checkout", List.of(capability("dev.ucp.shopping.checkout"))
        )));
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("93.184.216.34"))),
                mock(MerchantEnrichmentCandidateService.class));

        CartRoutingTarget target = provider.resolve(offer()).orElseThrow();

        assertThat(target.merchantProvider().executionPolicy()
                .decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isTrue();
        assertThat(target.merchantIntegrationId()).isNull();
    }

    @Test
    void freshUsableMerchantDomainIsZeroDiscoveryHotPath() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        MerchantCartProvider local = new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/api/ucp/mcp", null,
                List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(),
                java.util.Set.of("dev.ucp.shopping.cart"));
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(local));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                publicUrlValidator(),
                mock(MerchantEnrichmentCandidateService.class));

        var target = provider.resolve(offer()).orElseThrow();

        assertThat(target.merchantIntegrationId()).isNull();
        assertThat(target.merchantProvider().merchantId()).isNull();
        assertThat(target.scopeKey())
                .isEqualTo("SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example");
        verify(lookup).findActiveByCanonicalDomain("shop.example");
        verifyNoMoreInteractions(lookup);
        verifyNoInteractions(profiles);
    }

    @Test
    void existingIntegrationDoesNotChangeExternalOfferScope() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        UUID integrationId = UUID.randomUUID();
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(List.of(new CommerceCapabilityDecision(
                CommerceOperation.CART, true, CapabilityAuthorizationDecision.notRequired(), true,
                CapabilityIntegrationHealth.HEALTHY, true, CapabilityAvailability.AVAILABLE,
                CommerceExecutionRail.PROVIDER_CART, List.of(), integrationId,
                MerchantIntegrationProvider.SHOPIFY)));
        MerchantCartProvider local = new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/api/ucp/mcp", null,
                List.of(), policy, Instant.now(), java.util.Set.of("dev.ucp.shopping.cart"));
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(local));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles, publicUrlValidator(),
                mock(MerchantEnrichmentCandidateService.class));

        var target = provider.resolve(offer()).orElseThrow();

        assertThat(target.merchantIntegrationId()).isNull();
        assertThat(target.scopeKey())
                .isEqualTo("SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example");
        verifyNoInteractions(profiles);
    }

    @Test
    void domainMissDiscoversMerchantProfileOnceAndUsesAdvertisedEndpoint() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.empty());
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(eq("shop.example"), any())).thenReturn(profile());
        MerchantEnrichmentCandidateService enrichmentCandidates = mock(MerchantEnrichmentCandidateService.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("93.184.216.34"))),
                enrichmentCandidates);

        var first = provider.resolve(offer()).orElseThrow();
        var second = provider.resolve(offer()).orElseThrow();

        assertThat(first.merchantProvider().merchantId()).isNull();
        assertThat(first.scopeKey())
                .isEqualTo("SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example");
        assertThat(first.merchantProvider().domain()).isEqualTo("shop.example");
        assertThat(first.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo("https://shop.example/api/ucp/mcp");
        assertThat(second.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo(first.merchantProvider().advertisedMcpEndpoint());
        verify(profiles).fetchProfileResult(eq("shop.example"), any());
        verify(enrichmentCandidates).enqueue("shop.example");
    }

    @Test
    void staleLocalProfileRefreshesExactlyOnceWithoutChangingIntegrationIdentity() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        UUID integrationId = UUID.randomUUID();
        MerchantExecutionPolicy policy = new MerchantExecutionPolicy(List.of(new CommerceCapabilityDecision(
                CommerceOperation.CART, true, CapabilityAuthorizationDecision.notRequired(), true,
                CapabilityIntegrationHealth.HEALTHY, true, CapabilityAvailability.AVAILABLE,
                CommerceExecutionRail.PROVIDER_CART, List.of(), integrationId,
                MerchantIntegrationProvider.SHOPIFY)));
        MerchantCartProvider local = new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/stale", null,
                List.of(), policy, Instant.now().minus(Duration.ofDays(36)),
                java.util.Set.of("dev.ucp.shopping.cart"));
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(local));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(eq("shop.example"), any())).thenReturn(profile());
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("93.184.216.34"))),
                mock(MerchantEnrichmentCandidateService.class));

        var target = provider.resolve(offer()).orElseThrow();

        assertThat(target.merchantIntegrationId()).isNull();
        assertThat(target.scopeKey())
                .isEqualTo("SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example");
        assertThat(target.merchantProvider().advertisedMcpEndpoint())
                .isEqualTo("https://shop.example/api/ucp/mcp");
        verify(profiles).fetchProfileResult(eq("shop.example"), any());
    }

    @Test
    void profileWithoutCartCapabilityFailsClosed() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/api/ucp/mcp", null,
                List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(), java.util.Set.of())));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(any(), any())).thenReturn(profile(Map.of()));
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("93.184.216.34"))),
                mock(MerchantEnrichmentCandidateService.class));

        assertThat(provider.resolve(offer())).isEmpty();
        verify(profiles).fetchProfileResult(eq("shop.example"), any());
    }

    @Test
    void persistedExternalRouteUsesFreshMerchantDomainMetadataWithoutDiscovery() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/api/ucp/mcp", null,
                List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(),
                java.util.Set.of("dev.ucp.shopping.cart", "dev.ucp.shopping.checkout"))));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles, publicUrlValidator(),
                mock(MerchantEnrichmentCandidateService.class));
        CartRoutingTarget persisted = new CartRoutingTarget(
                "SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example",
                MerchantIntegrationProvider.SHOPIFY, null, "gid://shopify/Shop/1",
                new MerchantCartProvider(null, "shop.example", "https://shop.example/old", null));

        CartRoutingTarget restored = provider.restore(persisted).orElseThrow();

        assertThat(restored.scopeKey()).isEqualTo(persisted.scopeKey());
        assertThat(restored.merchantIntegrationId()).isNull();
        assertThat(restored.merchantProvider().merchantId()).isNull();
        verifyNoInteractions(profiles);
    }

    @Test
    void persistedCartOnlyDatasetProfileObservesCheckoutCapabilityAndEnablesEmbeddedRail() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain("shop.example")).thenReturn(Optional.of(new MerchantCartProvider(
                UUID.randomUUID(), "shop.example", "https://shop.example/api/ucp/mcp", null,
                List.of(), MerchantExecutionPolicy.unavailable(), Instant.now(),
                java.util.Set.of("dev.ucp.shopping.cart"))));
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(eq("shop.example"), any())).thenReturn(profile(Map.of(
                "dev.ucp.shopping.cart", List.of(capability("dev.ucp.shopping.cart")),
                "dev.ucp.shopping.checkout", List.of(capability("dev.ucp.shopping.checkout"))
        )));
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles, publicUrlValidator(),
                mock(MerchantEnrichmentCandidateService.class));
        CartRoutingTarget persisted = new CartRoutingTarget(
                "SHOPIFY:merchant:gid://shopify/Shop/1:domain:shop.example",
                MerchantIntegrationProvider.SHOPIFY, null, "gid://shopify/Shop/1",
                new MerchantCartProvider(null, "shop.example", "https://shop.example/old", null));

        CartRoutingTarget first = provider.restoreForCheckout(persisted).orElseThrow();
        CartRoutingTarget second = provider.restoreForCheckout(persisted).orElseThrow();

        assertThat(first.merchantProvider().executionPolicy()
                .decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isTrue();
        assertThat(second.merchantProvider().executionPolicy()
                .decision(CommerceOperation.EMBEDDED_CHECKOUT).available()).isTrue();
        verify(profiles).fetchProfileResult(eq("shop.example"), any());
    }

    @Test
    void advertisedEndpointHostSubstitutionFailsClosed() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain(any())).thenReturn(Optional.empty());
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(any(), any())).thenReturn(profile("https://attacker.example/mcp"));
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("93.184.216.34"))),
                mock(MerchantEnrichmentCandidateService.class));

        assertThat(provider.resolve(offer())).isEmpty();
    }

    @Test
    void privateSellerDomainResolutionIsBlockedBeforeProfileFetch() throws Exception {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain(any())).thenReturn(Optional.empty());
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host ->
                        List.of(InetAddress.getByName("127.0.0.1"))),
                mock(MerchantEnrichmentCandidateService.class));

        assertThat(provider.resolve(offer())).isEmpty();
        verifyNoInteractions(profiles);
    }

    @Test
    void missingTrustedSellerDomainIsNotGuessed() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        ShopifyExternalOfferCartRoutingProvider provider = routingProvider(
                properties(), lookup, profiles,
                MerchantOutboundUrlValidator.withResolver(host -> List.of(InetAddress.getLoopbackAddress())),
                mock(MerchantEnrichmentCandidateService.class));

        assertThat(provider.resolve(offer(null))).isEmpty();
        verifyNoInteractions(lookup, profiles);
    }

    @Test
    void cancelContextReachesAnonymousMerchantTransportAsStableHeader() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class))).thenReturn(new MerchantMcpToolCallResult(
                "https://shop.example/api/ucp/mcp", "{}", null, NegotiatedCapabilities.none()));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());
        var target = routingProvider.resolve(offer()).orElseThrow();
        UUID key = UUID.randomUUID();

        transport.call(target, "cancel_cart", Map.of("cart_id", "cart"),
                new CartToolCallContext(key, "203.0.113.42"));

        verify(client).call(any(), eq(CommerceOperation.CART), eq("cancel_cart"), any(),
                eq(Map.of(
                        "Idempotency-Key", key.toString(),
                        ShopifyCartToolTransport.BUYER_IP_HEADER, "203.0.113.42"
                )), eq(true));
    }

    @Test
    void buyerIpReachesShopifyCartTransportAsServerControlledHeader() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class))).thenReturn(
                new MerchantMcpToolCallResult(
                        "https://shop.example/api/ucp/mcp", "{}", null, NegotiatedCapabilities.none()));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());

        transport.call(
                routingProvider.resolve(offer()).orElseThrow(),
                "create_cart",
                Map.of(),
                CartToolCallContext.forBuyer("203.0.113.42")
        );

        verify(client).call(any(), eq(CommerceOperation.CART), eq("create_cart"), any(),
                eq(Map.of(ShopifyCartToolTransport.BUYER_IP_HEADER, "203.0.113.42")), eq(true));
    }

    @Test
    void cancelRetryKeepsTheSameIdempotencyHeader() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("stale route"))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://shop.example/api/ucp/mcp", "{}", null, NegotiatedCapabilities.none()));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());
        var target = routingProvider.resolve(offer()).orElseThrow();
        UUID key = UUID.randomUUID();

        transport.call(target, "cancel_cart", Map.of("cart_id", "cart"), new CartToolCallContext(key));

        verify(client).call(any(), eq(CommerceOperation.CART), eq("cancel_cart"), any(),
                eq(Map.of("Idempotency-Key", key.toString())), eq(true));
        verify(client).call(any(), eq(CommerceOperation.CART), eq("cancel_cart"), any(),
                eq(Map.of("Idempotency-Key", key.toString())), eq(false));
    }

    @Test
    void createFailureIsNotRetried() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("ambiguous timeout"));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());

        assertThatThrownBy(() -> transport.call(
                routingProvider.resolve(offer()).orElseThrow(), "create_cart", Map.of(), CartToolCallContext.standard()))
                .isInstanceOf(com.meant.api.module.cart.exception.CartException.class);

        verify(client).call(any(), eq(CommerceOperation.CART), eq("create_cart"), any(), eq(Map.of()), eq(true));
    }

    @Test
    void updateFailureIsNotRetried() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("ambiguous timeout"));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());

        assertThatThrownBy(() -> transport.call(
                routingProvider.resolve(offer()).orElseThrow(), "update_cart", Map.of(), CartToolCallContext.standard()))
                .isInstanceOf(com.meant.api.module.cart.exception.CartException.class);

        verify(client).call(any(), eq(CommerceOperation.CART), eq("update_cart"), any(), eq(Map.of()), eq(true));
    }

    @Test
    void getFailureRefreshesAndRetriesOnce() {
        ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
        when(client.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("stale endpoint"))
                .thenReturn(new MerchantMcpToolCallResult(
                        "https://shop.example/api/ucp/mcp", "{}", null, NegotiatedCapabilities.none()));
        ShopifyExternalOfferCartRoutingProvider routingProvider = routeProvider();
        ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                client, new CartBindingMetrics(new SimpleMeterRegistry()), routingProvider, retryPolicy());

        transport.call(
                routingProvider.resolve(offer()).orElseThrow(), "get_cart", Map.of(), CartToolCallContext.standard());

        verify(client).call(any(), eq(CommerceOperation.CART), eq("get_cart"), any(), eq(Map.of()), eq(true));
        verify(client).call(any(), eq(CommerceOperation.CART), eq("get_cart"), any(), eq(Map.of()), eq(false));
    }

    @Test
    void managedGetAndCancelRetryOnTheExactIntegrationWithoutExternalRouteRefresh() {
        for (String tool : List.of("get_cart", "cancel_cart")) {
            ShopifyMerchantUcpTransport client = mock(ShopifyMerchantUcpTransport.class);
            when(client.call(any(), any(), any(), any(), any(), any(Boolean.class)))
                    .thenThrow(new com.meant.api.provider.shopify.auth.ShopifyUcpTransportException(
                            com.meant.api.provider.shopify.auth.ShopifyUcpTransportFailure.TIMEOUT,
                            "timeout", null, null, null))
                    .thenReturn(new MerchantMcpToolCallResult(
                            "https://managed.shop/api/ucp/mcp", "{}", null, NegotiatedCapabilities.none()));
            ShopifyExternalOfferCartRoutingProvider external = mock(ShopifyExternalOfferCartRoutingProvider.class);
            ShopifyCartRetryPolicy retry = retryPolicy();
            ShopifyCartToolTransport transport = new ShopifyCartToolTransport(
                    client, new CartBindingMetrics(new SimpleMeterRegistry()), external, retry);
            UUID integrationId = UUID.randomUUID();
            var integration = new com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting(
                    integrationId, MerchantIntegrationProvider.SHOPIFY, Set.of(MerchantIntegrationRole.CART),
                    MerchantIntegrationStatus.ACTIVE, "shop-1", "managed.shop", "shop-1",
                    "https://managed.shop/api/ucp/mcp");
            CartRoutingTarget target = new CartRoutingTarget(
                    "SHOPIFY:integration:" + integrationId, MerchantIntegrationProvider.SHOPIFY,
                    integrationId, "shop-1", new MerchantCartProvider(
                    UUID.randomUUID(), "managed.shop", "https://wrong.example/api/ucp/mcp", null,
                    List.of(integration), MerchantExecutionPolicy.unavailable(), Instant.now(),
                    Set.of("dev.ucp.shopping.cart")));
            UUID key = UUID.randomUUID();

            transport.call(target, tool, Map.of(), new CartToolCallContext(
                    tool.equals("cancel_cart") ? key : null));

            verify(client).call(eq(target), eq(CommerceOperation.CART), eq(tool), any(), any(), eq(true));
            verify(client).call(eq(target), eq(CommerceOperation.CART), eq(tool), any(), any(), eq(false));
            verifyNoInteractions(external);
        }
    }

    private ShopifyExternalOfferCartRoutingProvider routeProvider() {
        MerchantCartProviderLookupService lookup = mock(MerchantCartProviderLookupService.class);
        when(lookup.findActiveByCanonicalDomain(any())).thenReturn(Optional.empty());
        UcpProfileClient profiles = mock(UcpProfileClient.class);
        when(profiles.fetchProfileResult(any(), any())).thenReturn(profile());
        try {
            return routingProvider(properties(), lookup, profiles,
                    MerchantOutboundUrlValidator.withResolver(host ->
                            List.of(InetAddress.getByName("93.184.216.34"))),
                    mock(MerchantEnrichmentCandidateService.class));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private UcpProfileFetchResult profile() {
        return profile("https://shop.example/api/ucp/mcp");
    }

    private UcpProfileFetchResult profile(String endpoint) {
        return profile(endpoint, Map.of("dev.ucp.shopping.cart", List.of(new UcpCapabilityDefinition(
                "dev.ucp.shopping.cart", "2026-04-08", null, null, List.of(), null, Map.of()))));
    }

    private UcpProfileFetchResult profile(Map<String, List<UcpCapabilityDefinition>> capabilities) {
        return profile("https://shop.example/api/ucp/mcp", capabilities);
    }

    private UcpProfileFetchResult profile(
            String endpoint, Map<String, List<UcpCapabilityDefinition>> capabilities) {
        UcpProfile profile = new UcpProfile(
                "2026-04-08", Map.of(),
                Map.of("dev.ucp.shopping", List.of(new UcpServiceDefinition(
                        "dev.ucp.shopping", "2026-04-08", null, "mcp",
                        endpoint, null))),
                capabilities,
                Map.of());
        return new UcpProfileFetchResult(profile, "{}", "https://shop.example/.well-known/ucp", Instant.now());
    }

    private UcpCapabilityDefinition capability(String id) {
        return new UcpCapabilityDefinition(id, "2026-04-08", null, null, List.of(), null, Map.of());
    }

    private ShopifyCartProperties properties() {
        return new ShopifyCartProperties(
                Duration.ofDays(35), Duration.ofSeconds(2), Duration.ofSeconds(8), Duration.ofSeconds(10),
                Duration.ofSeconds(2));
    }

    private MerchantOutboundUrlValidator publicUrlValidator() {
        return MerchantOutboundUrlValidator.withResolver(host -> {
            try {
                return List.of(InetAddress.getByName("93.184.216.34"));
            } catch (Exception exception) {
                throw new java.net.UnknownHostException(host);
            }
        });
    }

    private ShopifyExternalOfferCartRoutingProvider routingProvider(
            ShopifyCartProperties properties,
            MerchantCartProviderLookupService lookup,
            UcpProfileClient profiles,
            MerchantOutboundUrlValidator validator,
            MerchantEnrichmentCandidateService enrichmentCandidates
    ) {
        MerchantUcpProfileObservationService observations = new MerchantUcpProfileObservationService(
                profiles,
                enrichmentCandidates,
                new MerchantUcpProfileObservationProperties(Duration.ofMinutes(10), 50)
        );
        com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties auth =
                mock(com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties.class);
        when(auth.isEnabled()).thenReturn(true);
        var readiness = new com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessProperties(
                com.meant.api.provider.shopify.capability.ShopifyAuthorizationTier.TOKEN,
                Set.of(), true, true, false, false, Set.of(), false, Set.of(), false);
        MerchantExecutionPolicyService executionPolicyService = new MerchantExecutionPolicyService(
                new MerchantExecutionPolicyProperties(true, true, true, true, false, true, true),
                new CapabilityExecutionPolicyEvaluator(),
                List.of(new ShopifyCapabilityReadinessAdapter(readiness, auth)));
        return new ShopifyExternalOfferCartRoutingProvider(
                properties, lookup, observations, validator, executionPolicyService);
    }

    private ShopifyCartRetryPolicy retryPolicy() {
        ShopifyCartRetryPolicy policy = mock(ShopifyCartRetryPolicy.class);
        when(policy.prepare(any())).thenReturn(true);
        when(policy.refreshExternalRoute(any())).thenReturn(true);
        return policy;
    }

    private ResolvedSelectedOffer offer() {
        return offer("shop.example");
    }

    private ResolvedSelectedOffer offer(String domain) {
        ProviderIdentity provider = new ProviderIdentity("SHOPIFY");
        ExternalIdentifier merchant = id(ExternalIdentifierType.MERCHANT, "gid://shopify/Shop/1");
        ExternalIdentifier product = id(ExternalIdentifierType.PRODUCT, "gid://shopify/Product/1");
        ExternalIdentifier variant = id(ExternalIdentifierType.VARIANT, "gid://shopify/ProductVariant/1");
        DiscoverySourceIdentity source = new DiscoverySourceIdentity(
                provider, ResultSourceType.PROVIDER_CATALOG, "SHOPIFY_GLOBAL_CATALOG");
        OfferIdentity identity = new OfferIdentity(
                provider, OfferMerchantScope.external(merchant), product, variant, List.of(), List.of(), null);
        ResultProvenance provenance = new ResultProvenance(
                provider, source, null, merchant, domain, product, variant,
                new ResultFreshness(Instant.now(), null),
                new ResultSourceReference(ResultSourceType.PROVIDER_CATALOG, "catalog", null));
        CatalogProductReference reference = new CatalogProductReference(
                identity.key(), source, null, null, merchant, domain, product, variant, List.of());
        return new ResolvedSelectedOffer("canonical", identity.key(), identity, provenance, reference);
    }

    private ExternalIdentifier id(ExternalIdentifierType type, String value) {
        return new ExternalIdentifier(type, "SHOPIFY", value);
    }
}

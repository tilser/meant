package com.meant.api.provider.shopify.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meant.api.module.cart.service.dto.CartRoutingTarget;
import com.meant.api.module.merchant.constant.CommerceOperation;
import com.meant.api.module.merchant.constant.MerchantIntegrationProvider;
import com.meant.api.module.merchant.constant.MerchantIntegrationRole;
import com.meant.api.module.merchant.constant.MerchantIntegrationStatus;
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.module.merchant.service.dto.MerchantIntegrationRouting;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShopifyMerchantUcpTransportTest {

    @Test
    void callsOnlyTheExactVerifiedMerchantAuthorityWithTokenTierOptionsAndIdempotency() throws Exception {
        ShopifyUcpClient client = mock(ShopifyUcpClient.class);
        when(client.callTool(any(), eq("cancel_cart"), any(), any())).thenReturn(
                new UcpToolResponse("{}", null, NegotiatedCapabilities.none()));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ShopifyMerchantUcpTransport transport = transport(client, registry);

        var result = transport.call(target("shop.example", "https://shop.example/api/ucp/mcp"),
                CommerceOperation.CART, "cancel_cart", Map.of("id", "cart-1"),
                Map.of("Idempotency-Key", "8cc80496-3ce8-4ab7-b7ca-c504647ddf12"));

        assertThat(result.endpoint()).isEqualTo("https://shop.example/api/ucp/mcp");
        verify(client).callTool(
                org.mockito.ArgumentMatchers.argThat(options ->
                        options.endpoint().toString().equals("https://shop.example/api/ucp/mcp")
                                && options.allowedHosts().equals(java.util.Set.of("shop.example"))
                                && options.requiredScopes().isEmpty()),
                eq("cancel_cart"), any(),
                eq(Map.of("Idempotency-Key", "8cc80496-3ce8-4ab7-b7ca-c504647ddf12")));
        assertThat(registry.get("commerce.provider.calls").counter().count()).isEqualTo(1);
    }

    @Test
    void substitutedAuthorityIsRejectedBeforeAuthenticationCanRun() throws Exception {
        ShopifyUcpClient client = mock(ShopifyUcpClient.class);
        ShopifyMerchantUcpTransport transport = transport(client, new SimpleMeterRegistry());

        assertThatThrownBy(() -> transport.call(
                target("shop.example", "https://attacker.example/api/ucp/mcp"),
                CommerceOperation.CHECKOUT_SESSION, "create_checkout", Map.of()))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(client);
    }

    @Test
    void managedMerchantUsesTechnicalIntegrationAuthorityWithoutExposingItAsMerchantDomain() throws Exception {
        ShopifyUcpClient client = mock(ShopifyUcpClient.class);
        when(client.callTool(any(), eq("create_cart"), any(), any())).thenReturn(
                new UcpToolResponse("{}", null, NegotiatedCapabilities.none()));
        ShopifyMerchantUcpTransport transport = transport(client, new SimpleMeterRegistry());
        UUID integrationId = UUID.randomUUID();
        String routingDomain = "weareallbirds.myshopify.com";
        String endpoint = "https://" + routingDomain + "/api/ucp/mcp";
        MerchantIntegrationRouting integration = new MerchantIntegrationRouting(
                integrationId,
                MerchantIntegrationProvider.SHOPIFY,
                Set.of(MerchantIntegrationRole.CART),
                MerchantIntegrationStatus.ACTIVE,
                "gid://shopify/Shop/1",
                "allbirds.com",
                "gid://shopify/Shop/1",
                endpoint
        );
        MerchantCartProvider provider = new MerchantCartProvider(
                UUID.randomUUID(),
                "allbirds.com",
                "allbirds.com",
                "https://allbirds.com/not-the-shopify-route",
                null,
                List.of(integration),
                MerchantExecutionPolicy.unavailable(),
                Instant.now(),
                Set.of("dev.ucp.shopping.cart")
        );
        CartRoutingTarget target = new CartRoutingTarget(
                "SHOPIFY:integration:" + integrationId,
                MerchantIntegrationProvider.SHOPIFY,
                integrationId,
                "gid://shopify/Shop/1",
                provider
        );

        transport.call(target, CommerceOperation.CART, "create_cart", Map.of());

        assertThat(target.merchantProvider().merchantDomain()).isEqualTo("allbirds.com");
        verify(client).callTool(
                org.mockito.ArgumentMatchers.argThat(options ->
                        options.endpoint().toString().equals(endpoint)
                                && options.allowedHosts().equals(Set.of(routingDomain))),
                eq("create_cart"),
                any(),
                eq(Map.of())
        );
    }

    private ShopifyMerchantUcpTransport transport(ShopifyUcpClient client, SimpleMeterRegistry registry)
            throws Exception {
        MerchantOutboundUrlValidator validator = MerchantOutboundUrlValidator.withResolver(host ->
                List.of(InetAddress.getByName("93.184.216.34")));
        return new ShopifyMerchantUcpTransport(client,
                new ShopifyCartProperties(Duration.ofDays(35), Duration.ofSeconds(2),
                        Duration.ofSeconds(8), Duration.ofSeconds(10), Duration.ofSeconds(2)),
                validator, new ShopifyCommerceMetrics(registry));
    }

    private CartRoutingTarget target(String domain, String endpoint) {
        return new CartRoutingTarget("SHOPIFY:merchant:shop-1", MerchantIntegrationProvider.SHOPIFY,
                null, "shop-1", new MerchantCartProvider(null, domain, endpoint, null,
                List.of(), MerchantExecutionPolicy.unavailable()));
    }
}

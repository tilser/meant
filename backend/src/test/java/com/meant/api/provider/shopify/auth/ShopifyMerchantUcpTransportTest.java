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
import com.meant.api.module.merchant.service.MerchantOutboundUrlValidator;
import com.meant.api.module.merchant.service.dto.MerchantCartProvider;
import com.meant.api.module.merchant.service.dto.MerchantExecutionPolicy;
import com.meant.api.plugin.spi.NegotiatedCapabilities;
import com.meant.api.plugin.spi.UcpToolResponse;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

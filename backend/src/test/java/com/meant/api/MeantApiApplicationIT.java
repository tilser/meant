package com.meant.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.meant.api.module.cart.service.MerchantCartPluginDispatchService;
import com.meant.api.module.catalog.service.FederatedCatalogDiscoveryService;
import com.meant.api.module.checkout.service.MerchantCheckoutPluginDispatchService;
import com.meant.api.module.merchant.service.MerchantCatalogPluginDispatchService;
import com.meant.api.module.order.service.MerchantOrderPluginDispatchService;
import com.meant.api.plugin.catalog.extension.CatalogExtensionRegistry;
import com.meant.api.provider.shopify.auth.ShopifyAuthenticatedUcpClient;
import com.meant.api.provider.shopify.catalog.ShopifyGlobalCatalogProvider;
import com.meant.api.provider.shopify.order.ShopifyOrderWebhookController;
import com.meant.api.provider.shopify.review.ShopifyReviewProductIdNormalizationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class MeantApiApplicationIT extends PostgresIntegrationTestSupport {

    @Test
    void contextLoads() {
    }

    @Test
    void discoversMovedArchitectureBeansExactlyOnce(ApplicationContext context) {
        assertSingleBean(context, CatalogExtensionRegistry.class);
        assertSingleBean(context, ShopifyAuthenticatedUcpClient.class);
        assertSingleBean(context, ShopifyGlobalCatalogProvider.class);
        assertSingleBean(context, ShopifyOrderWebhookController.class);
        assertSingleBean(context, ShopifyReviewProductIdNormalizationStrategy.class);
        assertSingleBean(context, FederatedCatalogDiscoveryService.class);
        assertSingleBean(context, MerchantCatalogPluginDispatchService.class);
        assertSingleBean(context, MerchantCartPluginDispatchService.class);
        assertSingleBean(context, MerchantCheckoutPluginDispatchService.class);
        assertSingleBean(context, MerchantOrderPluginDispatchService.class);
    }

    private <T> void assertSingleBean(ApplicationContext context, Class<T> type) {
        assertThat(context.getBeansOfType(type)).hasSize(1);
    }
}

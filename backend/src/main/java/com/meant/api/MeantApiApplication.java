package com.meant.api;

import com.meant.api.common.properties.CorsProperties;
import com.meant.api.module.agent.properties.AgentProperties;
import com.meant.api.module.cart.properties.CartRetryProperties;
import com.meant.api.module.catalog.properties.FederatedCatalogDiscoveryProperties;
import com.meant.api.module.checkout.properties.EmbeddedCheckoutProperties;
import com.meant.api.module.discount.properties.DiscountCodeSearchProperties;
import com.meant.api.module.merchant.properties.CrawlingProperties;
import com.meant.api.module.review.properties.ReviewCacheProperties;
import com.meant.api.module.user.properties.UserCollectionProperties;
import com.meant.api.plugin.catalog.extension.shopify.ShopifyGlobalCatalogExtensionProperties;
import com.meant.api.plugin.signing.SigningKeyProperties;
import com.meant.api.plugin.transport.client.UcpMcpDiagnosticsProperties;
import com.meant.api.plugin.transport.profile.AgentIdentity;
import com.meant.api.provider.shopify.auth.ShopifyAgentAuthProperties;
import com.meant.api.provider.shopify.capability.ShopifyCapabilityReadinessProperties;
import com.meant.api.provider.shopify.cart.ShopifyCartProperties;
import com.meant.api.provider.shopify.catalog.ShopifyGlobalCatalogProperties;
import com.meant.api.provider.shopify.order.ShopifyOrderWebhookProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(proxyBeanMethods = false)
@ConfigurationPropertiesScan(basePackageClasses = {
        CorsProperties.class,
        AgentProperties.class,
        CartRetryProperties.class,
        FederatedCatalogDiscoveryProperties.class,
        EmbeddedCheckoutProperties.class,
        DiscountCodeSearchProperties.class,
        CrawlingProperties.class,
        ReviewCacheProperties.class,
        UserCollectionProperties.class,
        ShopifyGlobalCatalogExtensionProperties.class,
        SigningKeyProperties.class,
        UcpMcpDiagnosticsProperties.class,
        AgentIdentity.class,
        ShopifyAgentAuthProperties.class,
        ShopifyCapabilityReadinessProperties.class,
        ShopifyCartProperties.class,
        ShopifyGlobalCatalogProperties.class,
        ShopifyOrderWebhookProperties.class
})
public class MeantApiApplication {

    static void main(String[] args) {
        SpringApplication.run(MeantApiApplication.class, args);
    }
}
